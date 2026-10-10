package com.hxy132465.launcherswitcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import rikka.shizuku.Shizuku

/**
 * Thin wrapper around the Shizuku API used to run privileged commands through a [ShizukuUserService].
 *
 * Two categories of operations:
 * 1. Launcher switching (shell-level): [setHomeActivity], [clearHomeActivity] — works with ADB
 * 2. Device Owner provisioning (root-level): [setDeviceOwner] — requires Shizuku running as root
 */
class ShizukuHelper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: IUserService? = null

    @Volatile
    private var bound = false

    /** Queue of pending launcher-switch requests. */
    private val pendingQueue = ConcurrentLinkedQueue<Pair<String, ((Boolean, String) -> Unit)>>()

    /** Queue of pending device-owner requests. */
    private val pendingDeviceOwnerQueue = ConcurrentLinkedQueue<((Boolean, String) -> Unit)>()

    private val userServiceArgs: Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("launcher")
            .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IUserService.Stub.asInterface(binder)
            } else {
                null
            }
            // onServiceConnected may fire on a Binder thread; dispatch drain to main thread.
            mainHandler.post {
                drainPending()
                drainDeviceOwnerPending()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    // ─── public queries ───────────────────────────────────────────────────────

    fun isBinderAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isPermissionGranted(): Boolean {
        if (!isBinderAvailable()) return false
        return runCatching {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * Returns true if Shizuku is running with ROOT identity (uid 0).
     * Device Owner provisioning via `dpm set-device-owner` only works in this mode.
     */
    fun isRootMode(): Boolean = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    // ─── bind / unbind ────────────────────────────────────────────────────────

    fun bind() {
        if (bound || !isPermissionGranted()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
            bound = true
        } catch (ignored: Throwable) {
            bound = false
        }
    }

    fun unbind() {
        if (!bound) return
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        bound = false
        service = null
    }

    // ─── launcher switching (works as shell) ──────────────────────────────────

    /**
     * Sets the default home activity. [component] must be a flattened component name
     * ("package/class"). Supports [clear] flag to clear the persistent home preference.
     */
    fun setHomeActivity(component: String, clear: Boolean = false, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val svc = service
            if (svc == null) {
                pendingQueue.add(component to callback)
                bind()
                if (!bound) {
                    pendingQueue.removeAll { entry -> entry.second === callback }
                    callback(false, "Shizuku is not authorized")
                }
            } else {
                execAsync(svc, component, clear, callback)
            }
        }
    }

    // ─── device owner provisioning (requires root) ────────────────────────────

    /**
     * Attempts to set this app as Device Owner via `dpm set-device-owner`.
     * Only succeeds when Shizuku is running as ROOT (Magisk/KernelSU).
     * When running as ADB shell (uid 2000), returns a clear error message
     * explaining the limitation and the manual ADB fallback.
     */
    fun setDeviceOwner(callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val svc = service
            if (svc == null) {
                pendingDeviceOwnerQueue.add(callback)
                bind()
                if (!bound) {
                    pendingDeviceOwnerQueue.removeAll { it === callback }
                    callback(false, buildShellFallbackMessage())
                }
            } else {
                execDeviceOwnerAsync(svc, callback)
            }
        }
    }

    // ─── internal helpers ─────────────────────────────────────────────────────

    private fun drainPending() {
        while (true) {
            val entry = pendingQueue.poll() ?: break
            val svc = service
            if (svc == null) {
                pendingQueue.add(entry)
                break
            }
            execAsync(svc, entry.first, false, entry.second)
        }
    }

    private fun drainDeviceOwnerPending() {
        while (true) {
            val cb = pendingDeviceOwnerQueue.poll() ?: break
            val svc = service
            if (svc == null) {
                pendingDeviceOwnerQueue.add(cb)
                break
            }
            execDeviceOwnerAsync(svc, cb)
        }
    }

    private fun execAsync(
        svc: IUserService,
        component: String,
        clear: Boolean,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            val result = try {
                val raw = if (clear) svc.clearHomeActivity(component) else svc.setHomeActivity(component)
                parseResult(raw)
            } catch (t: Throwable) {
                false to (t.message ?: t.javaClass.simpleName)
            }
            mainHandler.post { callback(result.first, result.second) }
        }.start()
    }

    private fun execDeviceOwnerAsync(
        svc: IUserService,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            val result = try {
                val raw = svc.setDeviceOwner()
                val (success, msg) = parseResult(raw)
                // If shell-mode failure, augment with helpful message.
                if (!success && raw.contains("Permission denied", ignoreCase = true)) {
                    false to buildShellFallbackMessage()
                } else {
                    success to msg
                }
            } catch (t: Throwable) {
                false to (t.message ?: t.javaClass.simpleName)
            }
            mainHandler.post { callback(result.first, result.second) }
        }.start()
    }

    private fun parseResult(output: String): Pair<Boolean, String> {
        val trimmed = output.trim()
        val lines = trimmed.split("\n", limit = 2)
        val firstLine = lines.getOrElse(0) { "" }
        return if (firstLine.startsWith("EXIT:")) {
            val exitCode = firstLine.removePrefix("EXIT:").toIntOrNull() ?: -1
            val remainder = lines.getOrElse(1) { "" }.trim()
            val failed = exitCode != 0 || remainder.startsWith("ERROR") ||
                remainder.contains("Error", ignoreCase = true) ||
                remainder.contains("Exception", ignoreCase = true)
            (!failed) to remainder.ifEmpty { if (exitCode == 0) "成功" else "EXIT:$exitCode" }
        } else {
            val failed = trimmed.startsWith("ERROR") ||
                trimmed.contains("Error", ignoreCase = true) ||
                trimmed.contains("Exception", ignoreCase = true)
            (!failed) to trimmed.ifEmpty { "OK" }
        }
    }

    /** Builds a user-friendly message explaining why the command failed in shell mode. */
    private fun buildShellFallbackMessage(): String {
        return """设备所有者设置需要 Root 权限。

当前 Shizuku 以 Shell 身份运行，无法执行 dpm 命令。

请选择以下方案之一：
1. 在已 Root 的设备上使用 Shizuku（Magisk/KernelSU）
2. 使用电脑 ADB 手动执行：
   adb shell dpm set-device-owner com.hxy132465.launcherswitcher/.MyDeviceAdminReceiver
   （要求设备无账户且为全新状态）"""
    }
}
