package com.example.launcherswitcher

import android.content.Context

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

    private fun exec(vararg command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(command)
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            (stdout + stderr).trim().ifEmpty { "OK" }
        } catch (t: Throwable) {
            "ERROR: ${t.message}"
        }
    }

    private companion object {
        const val CMD = "/system/bin/cmd"
    }
}

