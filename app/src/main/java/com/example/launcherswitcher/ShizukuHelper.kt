package com.example.launcherswitcher

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
 * Thin wrapper around the Shizuku API used to run the privileged shell command
 * {@code cmd package set-home-activity} through a [ShizukuUserService].
 */
class ShizukuHelper(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: IUserService? = null

    @Volatile
    private var bound = false

    /**
     * Queue of pending (component, clear-flag, callback) triples.
     * Uses ConcurrentLinkedQueue for lock-free thread-safe access from both
     * the main thread (setHomeActivity) and the Binder thread (drainPending
     * dispatched via mainHandler).
     */
    private val pendingQueue = ConcurrentLinkedQueue<Pair<String, ((Boolean, String) -> Unit)>>()

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
            mainHandler.post { drainPending() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun isBinderAvailable(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isPermissionGranted(): Boolean {
        if (!isBinderAvailable()) return false
        return runCatching {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

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
                    // Remove this specific callback (not the queued entry) so we don't leak it.
                    pendingQueue.removeAll { entry -> entry.second === callback }
                    callback(false, "Shizuku is not authorized")
                }
            } else {
                execAsync(svc, component, clear, callback)
            }
        }
    }

    /**
     * Called from mainHandler.post inside onServiceConnected to drain pending requests.
     * Runs on the main thread; safe to mutate pendingQueue without locking.
     */
    private fun drainPending() {
        while (true) {
            val entry = pendingQueue.poll() ?: break
            val svc = service
            if (svc == null) {
                // Re-enqueue so it's not lost if the service reconnects.
                pendingQueue.add(entry)
                break
            }
            execAsync(svc, entry.first, false, entry.second)
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

    /**
     * Parses the string returned by ShizukuUserService.
     * Format: "EXIT:<code>\n<remaining output>" or bare output for compatibility.
     */
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
            (!failed) to remainder.ifEmpty { if (exitCode == 0) "OK" else "EXIT:$exitCode" }
        } else {
            // Legacy format (no EXIT: prefix): fall back to text heuristics.
            val failed = trimmed.startsWith("ERROR") ||
                trimmed.contains("Error", ignoreCase = true) ||
                trimmed.contains("Exception", ignoreCase = true)
            (!failed) to trimmed.ifEmpty { "OK" }
        }
    }
}
