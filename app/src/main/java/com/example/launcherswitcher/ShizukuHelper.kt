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

    @Volatile
    private var pendingComponent: String? = null

    @Volatile
    private var pendingCallback: ((Boolean, String) -> Unit)? = null

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
            val comp = pendingComponent
            val cb = pendingCallback
            pendingComponent = null
            pendingCallback = null
            if (service != null && comp != null && cb != null) {
                execAsync(service!!, comp, cb)
            } else if (cb != null) {
                cb(false, "Shizuku user service unavailable")
            }
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
     * ("package/class"). The result is delivered on the main thread.
     */
    fun setHomeActivity(component: String, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val svc = service
            if (svc != null) {
                execAsync(svc, component, callback)
            } else {
                pendingComponent = component
                pendingCallback = callback
                bind()
                if (!bound) {
                    pendingComponent = null
                    pendingCallback = null
                    callback(false, "Shizuku is not authorized")
                }
            }
        }
    }

    private fun execAsync(
        svc: IUserService,
        component: String,
        callback: (Boolean, String) -> Unit
    ) {
        Thread {
            val result = try {
                val output = svc.setHomeActivity(component)
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
