package com.example.launcherswitcher

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Runs in a separate process with the identity of shell (ADB) or root, launched by the
 * Shizuku server. Because it is not a normal app process it is not subject to the non-SDK
 * interface restrictions, and it can execute shell commands with elevated identity.
 *
 * The only job here is to change the default home activity via the platform shell command
 * {@code cmd package set-home-activity}.
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

    override fun setHomeActivity(component: String): String =
        exec(CMD, "package", "set-home-activity", component)

    override fun clearHomeActivity(component: String): String {
        // Try to remove the ROLE_HOME holder for the package that matches the target launcher.
        // If the package is provided as a full component, extract the package part.
        val targetPackage = component.substringBefore('/', component)
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf(CMD, "role", "remove-role-holder", ROLE_HOME, targetPackage))
            val stdout = proc.inputStream.bufferedReader().use { it.readText() }
            val stderr = proc.errorStream.bufferedReader().use { it.readText() }
            proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            (stdout + stderr).trim().ifEmpty { "OK" }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun exec(vararg command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val success = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            if (!success) {
                process.destroyForcibly()
            }
            (stdout + stderr).trim().ifEmpty {
                if (success) "OK" else "TIMEOUT"
            }
        } catch (t: Throwable) {
            "ERROR: ${t.message}"
        }
    }

    companion object {
        const val CMD = "/system/bin/cmd"
        const val ROLE_HOME = "android.app.role.HOME"
        const val TIMEOUT_SECONDS = 10L
    }
}
