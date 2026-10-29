package com.itzfallenme.katmusic.data.presence

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.discord.socialsdk.rpc.IDiscordRpcCallback
import com.discord.socialsdk.rpc.IDiscordRpcConnection
import com.discord.socialsdk.rpc.IDiscordRpcService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Discord Rich Presence over Android IPC.
 *
 * The Discord app exports a small RPC service and speaks the same JSON protocol as the
 * desktop client, so presence can be published by binding to it - no partner SDK, no
 * native libraries, no extra permissions, and no user token. This is the approach the
 * LastWave client uses; the wire format and the service discovery are the same.
 *
 * Only the activity is sent, never a token: Discord attributes it to the application ID
 * the user configured, which is how the card gets its name and artwork.
 */
class DiscordRpcTransport private constructor(
    private val context: Context,
    private val serviceConnection: ServiceConnection,
    private val rpc: IDiscordRpcConnection
) {

    @Volatile
    private var closed = false

    val isOpen: Boolean
        get() = !closed && runCatching { rpc.asBinder().isBinderAlive }.getOrDefault(false)

    fun setActivity(activity: kotlinx.serialization.json.JsonObject): Boolean {
        if (!isOpen) return false
        return send(DiscordPresence.setActivityFrame(activity, Process.myPid(), UUID.randomUUID().toString()))
    }

    fun clearActivity(): Boolean {
        if (!isOpen) return false
        return send(DiscordPresence.clearActivityFrame(Process.myPid(), UUID.randomUUID().toString()))
    }

    private fun send(frame: String): Boolean {
        if (!isOpen) return false
        return runCatching {
            rpc.sendFrame(frame)
            true
        }.onFailure {
            Log.w(TAG, "Discord RPC sendFrame failed; dropping connection", it)
            close()
        }.getOrDefault(false)
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { rpc.disconnect() }
        runCatching { context.unbindService(serviceConnection) }
    }

    companion object {
        private const val TAG = "DiscordRpc"
        private const val RPC_ACTION = "com.discord.socialsdk.rpc.IDiscordRpcService"
        private const val RPC_VERSION = "1"

        private val DISCORD_PACKAGES = listOf(
            "com.discord",
            "com.discord.canary",
            "com.discord.ptb",
            "com.discord.debug"
        )

        private data class Bound(val binder: IBinder, val connection: ServiceConnection)

        /**
         * Finds Discord's RPC service.
         *
         * Enumerating each known package's exported services for an `rpc` class name comes
         * first: it survives the action string moving between Discord builds, which is the
         * usual reason a bind silently never happens. The documented action is the fallback.
         */
        fun findService(context: Context): ComponentName? {
            val pm = context.packageManager
            for (pkg in DISCORD_PACKAGES) {
                exportedService(pm, pkg)?.let { return it }
                resolvedService(pm, pkg)?.let { return it }
            }
            return null
        }

        fun isDiscordInstalled(context: Context): Boolean {
            if (findService(context) != null) return true
            return DISCORD_PACKAGES.any { pkg ->
                runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }
        }

        private fun exportedService(pm: PackageManager, pkg: String): ComponentName? {
            val services = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).services
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services
                }
            }.getOrNull() ?: return null
            return services.firstOrNull { info ->
                info.exported && info.name.orEmpty().contains("rpc", ignoreCase = true)
            }?.let { ComponentName(pkg, it.name) }
        }

        private fun resolvedService(pm: PackageManager, pkg: String): ComponentName? {
            val intent = Intent(RPC_ACTION).setPackage(pkg)
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }.firstOrNull()?.serviceInfo
            return info?.let { ComponentName(it.packageName, it.name) }
        }

        /**
         * Binds, then handshakes on a worker thread. Returns null for every failure mode -
         * Discord absent, refusing, or a dead binder - so callers can stay silent.
         */
        suspend fun connectOrNull(
            context: Context,
            applicationId: Long,
            timeoutMs: Long = 3_000L
        ): DiscordRpcTransport? {
            val component = runCatching { findService(context) }.getOrNull() ?: return null
            val appContext = context.applicationContext
            val bound = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<Bound?> { continuation ->
                    val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
                    fun finish(result: Bound?) {
                        if (resumed.compareAndSet(false, true) && continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder == null) finish(null) else finish(Bound(binder, this))
                        }

                        override fun onServiceDisconnected(name: ComponentName?) = Unit
                        override fun onBindingDied(name: ComponentName?) = finish(null)
                        override fun onNullBinding(name: ComponentName?) = finish(null)
                    }

                    val ok = runCatching {
                        appContext.bindService(
                            Intent(RPC_ACTION).setComponent(component),
                            connection,
                            Context.BIND_AUTO_CREATE
                        )
                    }.getOrDefault(false)

                    if (!ok) {
                        finish(null)
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation {
                        resumed.set(true)
                        runCatching { appContext.unbindService(connection) }
                    }
                }
            } ?: return null

            val service = IDiscordRpcService.Stub.asInterface(bound.binder)
            val callback = object : IDiscordRpcCallback.Stub() {
                override fun onFrame(frame: String?) = Unit
                override fun onClose(code: Int, message: String?) {
                    Log.d(TAG, "Discord closed the RPC connection: $code $message")
                }
            }
            val rpc = runCatching {
                service.connect(applicationId, RPC_VERSION, callback)
            }.getOrNull()
            if (rpc == null) {
                runCatching { appContext.unbindService(bound.connection) }
                return null
            }
            Log.d(TAG, "Discord RPC connected via $component")
            return DiscordRpcTransport(appContext, bound.connection, rpc)
        }
    }
}
