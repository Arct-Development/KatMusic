package com.itzfallenme.katmusic.ui.screens.blend

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itzfallenme.katmusic.data.blend.BlendMemberProfile
import com.itzfallenme.katmusic.data.blend.BlendSharePayload
import com.itzfallenme.katmusic.data.blend.BlendTasteProfile
import com.itzfallenme.katmusic.data.blend.BlendTasteProfileBuilder
import com.itzfallenme.katmusic.data.blend.GroupBlendEngine
import com.itzfallenme.katmusic.data.blend.GroupBlendResult
import com.itzfallenme.katmusic.data.repository.SongPlayStatsRepository
import com.itzfallenme.katmusic.playback.MusicPlayer
import com.itzfallenme.katmusic.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@kotlinx.serialization.Serializable
private data class SavedBlendMember(
    val userId: String,
    val displayName: String,
    val profile: BlendTasteProfile,
)

data class BlendMemberUi(
    val userId: String,
    val displayName: String,
    val isYou: Boolean,
    val trackCount: Int,
    val artistCount: Int,
)

data class GroupBlendUiState(
    val isLoading: Boolean = true,
    val myShareCode: String = "",
    val myTrackCount: Int = 0,
    val myArtistCount: Int = 0,
    val members: List<BlendMemberUi> = emptyList(),
    val result: GroupBlendResult = GroupBlendResult(0, emptyList(), emptyList(), emptyList()),
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class GroupBlendViewModel @Inject constructor(
    private val statsRepository: SongPlayStatsRepository,
    private val player: MusicPlayer,
    private val preferences: DataStore<Preferences>,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(GroupBlendUiState())
    val uiState: StateFlow<GroupBlendUiState> = _uiState.asStateFlow()
    private var myProfile = BlendTasteProfile()
    private val importedMembers = linkedMapOf<String, BlendMemberProfile>()

    private val savedMembersKey = stringPreferencesKey("group_blend_saved_members")

    init {
        refreshMyProfile()
        loadSavedMembers()
    }

    private fun loadSavedMembers() {
        viewModelScope.launch {
            val raw = runCatching { preferences.data.first()[savedMembersKey].orEmpty() }.getOrDefault("")
            if (raw.isBlank()) return@launch
            runCatching { json.decodeFromString<List<SavedBlendMember>>(raw) }
                .onSuccess { saved ->
                    importedMembers.clear()
                    saved.take(9).forEach { member ->
                        importedMembers[member.userId] = BlendMemberProfile(
                            member.userId, member.displayName, member.profile,
                        )
                    }
                    publishState()
                }
        }
    }

    private fun persistMembers() {
        val snapshot = importedMembers.values.map {
            SavedBlendMember(it.userId, it.displayName, it.profile)
        }
        viewModelScope.launch {
            runCatching {
                preferences.edit { prefs -> prefs[savedMembersKey] = json.encodeToString(snapshot) }
            }
        }
    }

    fun refreshMyProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            runCatching { withContext(Dispatchers.IO) { statsRepository.allStats() } }
                .onSuccess { stats ->
                    myProfile = BlendTasteProfileBuilder.build(stats)
                    publishState()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Couldn't read listening history.",
                    )
                }
        }
    }

    fun addMember(displayNameRaw: String, rawCode: String) {
        val displayName = displayNameRaw.trim().take(32)
        val code = rawCode.trim().replace("\\s".toRegex(), "")
        if (displayName.isBlank()) return setError("Enter your friend's display name.")
        if (code.length > 48_000) return setError("That share code is too large.")
        if (!code.startsWith(BlendSharePayload.PREFIX)) return setError("That isn't a KatMusic Blend code.")
        try {
            val bytes = Base64.decode(
                code.removePrefix(BlendSharePayload.PREFIX),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            )
            val payload = json.decodeFromString<BlendSharePayload>(bytes.decodeToString())
            require(payload.format == BlendSharePayload.FORMAT_VERSION) { "This Blend code version isn't supported." }
            require(payload.profile.schemaVersion == BlendTasteProfile.CURRENT_SCHEMA_VERSION) { "This profile version isn't supported." }
            require(payload.profile.artistAffinity.size <= BlendTasteProfile.MAX_ARTISTS)
            require(payload.profile.trackAffinity.size <= BlendTasteProfile.MAX_TRACKS)
            require(payload.profile.tracks.size <= BlendTasteProfile.MAX_TRACKS)
            require(payload.profile.artistAffinity.values.all { it.isFinite() && it in 0.0..1.0 })
            require(payload.profile.trackAffinity.values.all { it.isFinite() && it in 0.0..1.0 })
            require(payload.profile.tracks.all { it.title.length <= 300 && it.artist.length <= 300 })
            val id = payload.profile.fingerprint() + ":" + displayName.lowercase()
            if (id == myProfile.fingerprint()) return setError("That's your own Blend code.")
            if (importedMembers.containsKey(id)) return setError("That person is already in this Blend.")
            if (importedMembers.size >= 9) return setError("A Blend supports up to 10 people, including you.")
            importedMembers[id] = BlendMemberProfile(id, displayName, payload.profile)
            publishState()
            persistMembers()
            _uiState.value = _uiState.value.copy(message = "$displayName joined your Blend.", error = null)
        } catch (error: Exception) {
            setError(error.message?.takeIf { it.startsWith("This ") }
                ?: "Couldn't read that code. Ask your friend to copy it again.")
        }
    }

    fun playTrack(track: com.itzfallenme.katmusic.data.blend.BlendTrack) {
        val videoId = track.videoId?.takeIf { it.isNotBlank() }
        if (videoId == null) {
            setError("This track has no saved playable YouTube Music ID.")
            return
        }
        player.play(
            PlayableTrack(title = track.title, artist = track.artist, videoId = videoId),
            sourceLabel = "Group Blend",
            startRadio = false,
        )
    }

    fun removeMember(userId: String) {
        val removed = importedMembers.remove(userId) ?: return
        persistMembers()
        publishState()
        _uiState.value = _uiState.value.copy(message = "${removed.displayName} was removed.")
    }

    private fun publishState() {
        val members = listOf(BlendMemberProfile("you", "You", myProfile)) + importedMembers.values
        val result = GroupBlendEngine.calculate(members)
        val payload = BlendSharePayload(displayName = "KatMusic listener", profile = myProfile)
        val code = BlendSharePayload.PREFIX + Base64.encodeToString(
            json.encodeToString(payload).encodeToByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            myShareCode = code,
            myTrackCount = myProfile.tracks.size,
            myArtistCount = myProfile.artistAffinity.size,
            members = members.map {
                BlendMemberUi(it.userId, it.displayName, it.userId == "you",
                    it.profile.tracks.size, it.profile.artistAffinity.size)
            },
            result = result,
        )
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, message = null)
    }

    private fun BlendTasteProfile.fingerprint(): String =
        artistAffinity.entries.sortedBy { it.key }.joinToString("|") { "${it.key}:${it.value}" }
            .plus(trackAffinity.keys.sorted().joinToString("|"))
            .hashCode().toUInt().toString(16)
}
