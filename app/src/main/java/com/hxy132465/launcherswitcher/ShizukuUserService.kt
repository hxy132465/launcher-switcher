package com.hxy132465.launcherswitcher

import android.content.Context
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs in a separate process with the identity of shell (ADB, uid=2000) or root (uid=0),
 * launched by the Shizuku server. Because it is not a normal app process it is not subject
 * to the non-SDK interface restrictions, and it can execute shell commands with elevated identity.
 *
 * Capabilities:
 * - [setHomeActivity] / [clearHomeActivity]: switch/reset the default launcher (works as shell)
 * - [setDeviceOwner]: set this app as Device Owner via `dpm set-device-owner` (requires root)
 * - [getUid]: report current process UID so callers can detect root vs shell mode
 */
class ShizukuUserService : IUserService.Stub {

    constructor() : super()

    @Suppress("unused", "UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        destroy()
    }

    /** Returns the current process UID. 0 = root, 2000 = shell (ADB). */
    override fun getUid(): Int = android.system.Os.getuid()

    override fun setHomeActivity(component: String): String =
        exec(CMD, "package", "set-home-activity", component)

    override fun clearHomeActivity(component: String): String {
        val targetPackage = component.substringBefore('/', component)
        return exec(CMD, "role", "remove-role-holder", ROLE_HOME, targetPackage)
    }

    /**
     * Sets this app as the Device Owner by executing `dpm set-device-owner`.
     *
     * This command requires **root** (uid 0) on almost all Android versions.
     * When Shizuku is running as ADB shell (uid 2000), the command will fail
     * with a permission error — in that case, fall back to showing the manual
     * ADB command to the user.
     *
     * Prerequisites for success:
     * - Device has NO existing Device Owner
     * - Device has NO user accounts (Android enforces this)
     * - Shizuku is started with ROOT privilege (Magisk/KernelSU)
     */
    override fun setDeviceOwner(): String {
        val receiver = "com.hxy132465.launcherswitcher/.MyDeviceAdminReceiver"
        return exec("su", "-c", "dpm set-device-owner $receiver")
    }

    /**
     * Executes [command] synchronously with a timeout.
     * Returns "EXIT:<code>\n<output>" so the caller can unambiguously detect success.
     *
     * Protected by [execLock] to prevent concurrent shell executions from interleaving.
     */
    @Synchronized
    private fun exec(vararg command: String): String {
        if (!execLock.compareAndSet(false, true)) {
            return "EXIT:-1\nERROR: previous exec still running"
        }
        return try {
            val process = Runtime.getRuntime().exec(command)
            val success = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val output = (stdout + stderr).trim()
            val exitCode = if (success) process.exitValue() else -1
            if (!success) process.destroyForcibly()
            "EXIT:$exitCode\n$output"
        } catch (t: Throwable) {
            "EXIT:-1\nERROR: ${t.message}"
        } finally {
            execLock.set(false)
        }
    }

    companion object {
        const val CMD = "/system/bin/cmd"
        const val ROLE_HOME = "android.app.role.HOME"
        const val TIMEOUT_SECONDS = 15L
    }

    private val execLock = AtomicBoolean(false)
}
