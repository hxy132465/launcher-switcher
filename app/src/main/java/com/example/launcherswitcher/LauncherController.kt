package com.example.launcherswitcher

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.app.admin.IDevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.IInterface
import android.util.Log
import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener

/**
 * Wraps the Device Owner / Dhizuku / Shizuku pathways used to change the system default launcher.
 */
class LauncherController(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val ownAdmin: ComponentName = ComponentName(context, MyDeviceAdminReceiver::class.java)

    @Volatile
    private var dhizukuProxied = false

    private val homeFilter: IntentFilter
        get() = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    private val homeIntent: Intent
        get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    // ---------- privilege detection ----------

    fun isOwnDeviceOwner(): Boolean =
        runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    fun initDhizuku(): Boolean =
        runCatching { Dhizuku.init(context) }.getOrDefault(false)

    fun isDhizukuPermissionGranted(): Boolean =
        runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)

    fun dhizukuOwnerComponent(): ComponentName? =
        runCatching { Dhizuku.getOwnerComponent() }.getOrNull()

    fun requestDhizukuPermission(listener: DhizukuRequestPermissionListener) {
        Dhizuku.requestPermission(listener)
    }

    fun isDhizukuProxySuccessful(): Boolean = dhizukuProxied

    // ---------- actions ----------

    fun setLauncherAsDeviceOwner(component: ComponentName) {
        checkOwnDeviceOwner()
        dpm.addPersistentPreferredActivity(ownAdmin, homeFilter, component)
    }

    fun clearLauncherAsDeviceOwner(packageName: String) {
        checkOwnDeviceOwner()
        dpm.clearPackagePersistentPreferredActivities(ownAdmin, packageName)
    }

    fun setLauncherAsDhizuku(component: ComponentName): Result<Unit> = runCatching {
        ensureDhizukuProxy()
        dpm.addPersistentPreferredActivity(requireDhizukuOwner(), homeFilter, component)
    }

    fun clearLauncherAsDhizuku(packageName: String): Result<Unit> = runCatching {
        ensureDhizukuProxy()
        dpm.clearPackagePersistentPreferredActivities(requireDhizukuOwner(), packageName)
    }

    @Synchronized
    @SuppressLint("SoonBlockedPrivateApi")
    private fun ensureDhizukuProxy() {
        if (dhizukuProxied) return
        try {
            val field = DevicePolicyManager::class.java.getDeclaredField("mService")
            field.isAccessible = true
            val original = field.get(dpm) as IInterface
            val wrapped = Dhizuku.binderWrapper(original.asBinder())
            val proxy = IDevicePolicyManager.Stub.asInterface(wrapped)
            field.set(dpm, proxy)
            dhizukuProxied = true
            Log.i(TAG, "Dhizuku proxy installed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Dhizuku proxy", e)
            dhizukuProxied = false
            throw RuntimeException("Dhizuku proxy setup failed: ${e.message}", e)
        }
    }

    private fun requireDhizukuOwner(): ComponentName =
        dhizukuOwnerComponent() ?: throw IllegalStateException("Dhizuku owner component unavailable")

    private fun checkOwnDeviceOwner() {
        if (!isOwnDeviceOwner()) {
            throw SecurityException("App is not Device Owner")
        }
    }

    // ---------- queries ----------

    fun installedLaunchers(): List<LauncherInfo> {
        val pm = context.packageManager
        val results: List<ResolveInfo> = pm.queryIntentActivities(homeIntent, 0)
        return results
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                LauncherInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    component = component,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    fun currentLauncherComponent(): ComponentName? {
        val resolved = context.packageManager.resolveActivity(
            homeIntent, PackageManager.MATCH_DEFAULT_ONLY
        )
        val activityInfo = resolved?.activityInfo ?: return null
        return ComponentName(activityInfo.packageName, activityInfo.name)
    }

    companion object {
        private const val TAG = "LauncherController"
    }
}

data class LauncherInfo(
    val label: String,
    val component: ComponentName,
    val icon: Drawable
)
