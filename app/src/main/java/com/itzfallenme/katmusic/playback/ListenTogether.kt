package com.itzfallenme.katmusic.playback

import android.content.Context
import android.net.wifi.WifiManager
import com.itzfallenme.katmusic.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random

/** A track as it travels between phones; the receiver rebuilds a full [Track]. */
@Serializable
data class RoomTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String? = null,
    val artwork: String = "",
    val durationMs: Long = 0L
) {
    fun toTrack(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artwork = artwork,
        durationMs = durationMs
    )
}

/** One line of the room protocol. Unused fields stay at their defaults. */
@Serializable
data class RoomMessage(
    val type: String,
    val roomCode: String = "",
    val hostName: String = "",
    val guestName: String = "",
    val guests: List<String> = emptyList(),
    val track: RoomTrack? = null,
    val queue: List<RoomTrack> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val sentAt: Long = 0L
)

/** Encoding, decoding and the small pure helpers the sync loop relies on. */
object RoomProtocol {

    const val TCP_PORT = 47821
    const val DISCOVERY_PORT = 47822
    const val DISCOVERY_PREFIX = "KATROOM"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(message: RoomMessage): String = json.encodeToString(RoomMessage.serializer(), message) + "\n"

    fun decode(line: String?): RoomMessage? {
        val trimmed = line?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { json.decodeFromString(RoomMessage.serializer(), trimmed) }.getOrNull()
    }

    fun newCode(random: Random = Random.Default): String =
        buildString { repeat(4) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun discoveryPacket(code: String, hostName: String): String =
        "$DISCOVERY_PREFIX|$code|$hostName|$TCP_PORT"

    /** True when the packet advertises [code]. */
    fun matches(packet: String, code: String): Boolean {
        val parts = packet.split("|")
        return parts.size >= 4 &&
            parts[0] == DISCOVERY_PREFIX &&
            parts[1].equals(code, ignoreCase = true)
    }

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
}

/**
 * Two phones drifting apart is normal (network jitter, buffering); below this the
 * guest leaves playback alone instead of stuttering every heartbeat.
 */
const val ROOM_DRIFT_TOLERANCE_MS = 2_500L

fun needsResync(guestPositionMs: Long, hostPositionMs: Long): Boolean =
    abs(guestPositionMs - hostPositionMs) > ROOM_DRIFT_TOLERANCE_MS

data class ListenTogetherUiState(
    val hosting: Boolean = false,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val roomCode: String = "",
    val hostName: String = "",
    /** The host's own LAN address, shown so a guest can type it if discovery is blocked. */
    val hostAddress: String = "",
    val guests: List<String> = emptyList(),
    val error: String? = null
) {
    val active: Boolean get() = hosting || connected
}

/**
 * Listen Together over the local Wi-Fi.
 *
 * There is no server to run and no account to create: the host's phone opens a
 * TCP socket and advertises the room over UDP, guests join by typing the code,
 * and the host's playback state is pushed as JSON lines. The host leads; guests
 * follow track changes, play/pause and seeks. Sockets are kept in the IO scope
 * and closed whenever the room is left, so nothing keeps the radio awake after
 * the user stops.
 */
class ListenTogetherManager(
    private val context: Context,
    private val player: PlayerController
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ListenTogetherUiState())
    val state: StateFlow<ListenTogetherUiState> = _state.asStateFlow()

    private var server: ServerSocket? = null
    private var discoveryJob: Job? = null
    private var hostPushJob: Job? = null
    private var client: Socket? = null
    private var clientJob: Job? = null
    private var roomCode = ""
    private var hostName = ""
    private val guests = ConcurrentHashMap<Socket, String>()

    fun host(name: String) {
        leave()
        val clean = name.trim().replace('|', '-').ifBlank { "Host" }.take(30)
        val code = RoomProtocol.newCode()
        roomCode = code
        hostName = clean
        _state.value = ListenTogetherUiState(
            hosting = true,
            busy = true,
            roomCode = code,
            hostName = clean,
            hostAddress = localIpv4()
        )

        scope.launch {
            runCatching {
                val listener = ServerSocket()
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(RoomProtocol.TCP_PORT))
                server = listener
                _state.update { it.copy(busy = false) }
                startDiscovery(code, clean)
                startHostPush()
                while (isActive) {
                    val socket = listener.accept()
                    launch { handleGuest(socket) }
                }
            }.onFailure { error ->
                if (error !is java.net.SocketException) {
                    fail("Could not open the room on this network: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    fun join(code: String, name: String, address: String? = null) {
        val cleanCode = code.trim().uppercase().filter(Char::isLetterOrDigit).take(4)
        if (cleanCode.length != 4) {
            _state.value = ListenTogetherUiState(error = "Enter the 4-character room code.")
            return
        }
        val cleanName = name.trim().ifBlank { "Guest" }.take(30)
        leave()
        roomCode = cleanCode
        _state.value = ListenTogetherUiState(busy = true, roomCode = cleanCode)
        scope.launch {
            val host = address?.trim()?.takeIf { it.isNotBlank() } ?: discoverHost(cleanCode)
            if (host == null) {
                fail("No room called $cleanCode was found on this Wi-Fi. Check the code, and that both phones are on the same network.")
                return@launch
            }
            runCatching {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, RoomProtocol.TCP_PORT), 5_000)
                client = socket
                write(
                    socket,
                    RoomProtocol.encode(RoomMessage(type = "join", roomCode = cleanCode, guestName = cleanName))
                )
                _state.update { it.copy(busy = false, connected = true, hostName = host) }
                readHost(socket)
            }.onFailure { error ->
                disconnect("Lost the room: ${error.message ?: "connection closed"}")
            }
        }
    }

    fun leave() {
        discoveryJob?.cancel()
        discoveryJob = null
        hostPushJob?.cancel()
        hostPushJob = null
        runCatching { server?.close() }
        server = null
        runCatching { client?.close() }
        client = null
        clientJob?.cancel()
        clientJob = null
        guests.keys.forEach { socket -> runCatching { socket.close() } }
        guests.clear()
        _state.value = ListenTogetherUiState()
    }

    // ---- host -----------------------------------------------------------------

    private fun startDiscovery(code: String, name: String) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            val socket = runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull() ?: return@launch
            socket.use {
                val payload = RoomProtocol.discoveryPacket(code, name).toByteArray()
                val address = InetAddress.getByName("255.255.255.255")
                while (isActive) {
                    runCatching {
                        socket.send(DatagramPacket(payload, payload.size, address, RoomProtocol.DISCOVERY_PORT))
                    }
                    delay(2_000)
                }
            }
        }
    }

    private fun startHostPush() {
        hostPushJob?.cancel()
        hostPushJob = scope.launch {
            var lastTrack: String? = null
            var lastPlaying: Boolean? = null
            var lastSent = 0L
            player.state.collect { snapshot ->
                val now = System.currentTimeMillis()
                if (snapshot.track == null) return@collect
                val changed = snapshot.track.id != lastTrack || snapshot.playing != lastPlaying
                if (changed || now - lastSent >= 2_000L) {
                    lastTrack = snapshot.track.id
                    lastPlaying = snapshot.playing
                    lastSent = now
                    broadcast(stateMessage(snapshot))
                }
            }
        }
    }

    private suspend fun handleGuest(socket: Socket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val join = RoomProtocol.decode(reader.readLine())
        if (join == null || join.type != "join" || !join.roomCode.equals(roomCode, true)) {
            runCatching { socket.close() }
            return@withContext
        }
        val guestName = join.guestName.ifBlank { "Guest" }.take(30)
        guests[socket] = guestName
        write(socket, RoomProtocol.encode(RoomMessage(type = "welcome", roomCode = roomCode, hostName = hostName)))
        broadcastRoster()
        broadcast(stateMessage(player.state.value))
        try {
            while (scope.isActive) {
                // Guests do not send playback commands yet; reading keeps the
                // connection alive and detects a closed socket.
                if (reader.readLine() == null) break
            }
        } finally {
            guests.remove(socket)
            runCatching { socket.close() }
            broadcastRoster()
        }
    }

    private fun stateMessage(snapshot: PlayerUiState): RoomMessage = RoomMessage(
        type = "state",
        roomCode = roomCode,
        hostName = hostName,
        track = snapshot.track?.toRoomTrack(),
        queue = snapshot.queue.map { it.toRoomTrack() },
        index = snapshot.index,
        positionMs = snapshot.positionMs,
        playing = snapshot.playing,
        sentAt = System.currentTimeMillis(),
        guests = guests.values.toList()
    )

    private fun broadcastRoster() {
        val names = guests.values.toList()
        // The host's own UI reads the same field, so update it here as well as
        // sending the roster to the guests.
        _state.update { it.copy(guests = names) }
        broadcast(RoomMessage(type = "roster", roomCode = roomCode, hostName = hostName, guests = names))
    }

    private fun broadcast(message: RoomMessage) {
        val line = RoomProtocol.encode(message)
        guests.keys.forEach { socket -> write(socket, line) }
    }

    // ---- guest ----------------------------------------------------------------

    private suspend fun discoverHost(code: String): String? = withContext(Dispatchers.IO) {
        // Multicast lock keeps the radio from filtering broadcast packets while
        // we listen; without it some devices only receive them sporadically.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching {
            wifi?.createMulticastLock("katmusic-room")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(RoomProtocol.DISCOVERY_PORT))
                socket.soTimeout = 1_500
                val deadline = System.currentTimeMillis() + 8_000
                val buffer = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
                    if (!received) continue
                    val text = String(packet.data, 0, packet.length)
                    if (!RoomProtocol.matches(text, code)) continue
                    return@withContext packet.address.hostAddress
                }
            }
            null
        } finally {
            runCatching { lock?.release() }
        }
    }

    private suspend fun readHost(socket: Socket) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        try {
            while (scope.isActive) {
                val message = RoomProtocol.decode(reader.readLine())
                if (message == null) {
                    if (reader.ready()) continue
                    break
                }
                when (message.type) {
                    "welcome" -> _state.update {
                        it.copy(connected = true, busy = false, hostName = message.hostName.ifBlank { it.hostName })
                    }
                    "roster" -> _state.update { it.copy(guests = message.guests) }
                    "state" -> {
                        _state.update { it.copy(guests = message.guests) }
                        applyHostState(message)
                    }
                }
            }
        } finally {
            disconnect("The host ended the room.")
        }
    }

    /** Mirrors the host's transport without fighting it on every heartbeat. */
    private fun applyHostState(message: RoomMessage) {
        val incoming = message.track?.toTrack() ?: return
        val current = player.state.value
        val queue = message.queue.map { it.toTrack() }
        if (current.track?.id != incoming.id) {
            player.playTracks(
                queue.ifEmpty { listOf(incoming) },
                message.index.coerceIn(0, (queue.size - 1).coerceAtLeast(0)),
                autoPlay = message.playing
            )
            if (message.positionMs > 0) player.seekTo(message.positionMs)
            return
        }
        if (needsResync(current.positionMs, message.positionMs)) {
            player.seekTo(message.positionMs)
        }
        if (message.playing && !current.playing) {
            player.player.play()
        } else if (!message.playing && current.playing) {
            player.player.pause()
        }
    }

    // ---- plumbing -------------------------------------------------------------

    private fun write(socket: Socket, line: String) {
        runCatching {
            synchronized(socket) {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                writer.write(line)
                writer.flush()
            }
        }.onFailure {
            guests.remove(socket)
            runCatching { socket.close() }
        }
    }

    /** First site-local IPv4 address; empty when only mobile data is up. */
    private fun localIpv4(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull().orEmpty()

    private fun fail(message: String) {
        _state.value = ListenTogetherUiState(error = message)
    }

    private fun disconnect(message: String) {
        if (_state.value.active || _state.value.busy) {
            _state.value = ListenTogetherUiState(error = message)
        }
        runCatching { client?.close() }
        client = null
    }

    fun release() {
        leave()
        scope.cancel()
    }
}

private fun Track.toRoomTrack(): RoomTrack = RoomTrack(
    id = id,
    title = title,
    artist = artist,
    album = album,
    artwork = artwork,
    durationMs = durationMs
)
