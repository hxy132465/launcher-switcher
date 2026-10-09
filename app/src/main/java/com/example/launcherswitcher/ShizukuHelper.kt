package com.example.launcherswitcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

    /** Queue of pending (component, callback) pairs. Fixes race where rapid taps overwrite each other. */
    private val pendingQueue = mutableListOf<Pair<String, (Boolean, String) -> Unit>>()

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
            drainPending()
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
     * ("package/class"). Supports [clear] flag to clear the persistent home preference
     * via the system role command when possible.
     */
    fun setHomeActivity(component: String, clear: Boolean = false, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val svc = service
            if (svc == null) {
                pendingQueue.add(component to callback)
                bind()
                if (!bound) {
                    pendingQueue.removeAll { it.first == component && it.second === callback }
                    callback(false, "Shizuku is not authorized")
                }
            } else {
                execAsync(svc, component, clear, callback)
            }
        }
    }

    private fun drainPending() {
        while (pendingQueue.isNotEmpty()) {
            val (comp, cb) = pendingQueue.removeAt(0)
            val svc = service
            if (svc == null) break
            execAsync(svc, comp, false, cb)
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
                val output = if (clear) {
                    svc.clearHomeActivity(component)
                } else {
                    svc.setHomeActivity(component)
                }
                val failed = output.startsWith("ERROR") ||
                    output.contains("Error", ignoreCase = true) ||
                    output.contains("Exception", ignoreCase = true)
                (!failed) to output
            } catch (t: Throwable) {
                false to (t.message ?: t.javaClass.simpleName)
            }
            mainHandler.post { callback(result.first, result.second) }
        }.start()
    }
}
