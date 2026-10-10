package com.example.launcherswitcher

import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.launcherswitcher.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: LauncherController
    private lateinit var shizuku: ShizukuHelper
    private lateinit var prefs: SharedPreferences

    private val shizukuRequestCode = 1
    private val dhizukuRequestCode = 2

    @Volatile
    private var dhizukuPermissionGranted = false

    @Volatile
    private var dhizukuInitResult: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = LauncherController(this)
        shizuku = ShizukuHelper(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        binding.launcherList.layoutManager = LinearLayoutManager(this)
        binding.launcherList.adapter = LauncherAdapter(null) { confirmSwitch(it) }

        binding.refreshButton.setOnClickListener { refresh() }
        binding.authorizeButton.setOnClickListener { authorize() }
        binding.resetButton.setOnClickListener { resetDefault() }

        if (!prefs.getBoolean(KEY_WELCOME_SHOWN, false)) {
            prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply()
            showWelcomeDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onStop() {
        super.onStop()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        shizuku.unbind()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val isOwner = controller.isOwnDeviceOwner()
        binding.ownerStatus.text = getString(
            if (isOwner) R.string.device_owner_yes else R.string.device_owner_no
        )
        binding.setupHint.visibility = if (isOwner) View.GONE else View.VISIBLE
        binding.setupHint.text = getString(R.string.setup_hint)

        updateModeBadge()

        val current = controller.currentLauncherComponent()
        binding.currentLauncher.text = getString(
            R.string.current_launcher_format,
            current?.packageName ?: getString(android.R.string.unknownName)
        )

        dhizukuPermissionGranted = controller.initDhizuku() && controller.isDhizukuPermissionGranted()

        val launchers = controller.installedLaunchers()
        (binding.launcherList.adapter as? LauncherAdapter)?.submitList(launchers)
        (binding.launcherList.adapter as? LauncherAdapter)?.updateCurrent(current?.flattenToString())

        updateAuthorizeButton()
    }

    private fun updateModeBadge() {
        val mode = when {
            controller.isOwnDeviceOwner() -> ModeBadge.DEVICE_OWNER
            dhizukuPermissionGranted -> ModeBadge.DHIZUKU
            shizuku.isBinderAvailable() && shizuku.isPermissionGranted() -> ModeBadge.SHIZUKU
            else -> ModeBadge.NONE
        }
        binding.modeBadge.visibility = if (mode != ModeBadge.NONE) View.VISIBLE else View.GONE
        binding.modeBadge.setText(mode.labelRes)
        binding.modeBadge.setTextColor(mode.textColorRes?.let { getColor(it) } ?: android.graphics.Color.WHITE)
        binding.modeBadge.setBackgroundResource(mode.bgRes)
    }

    private fun updateAuthorizeButton() {
        val available = when {
            controller.isOwnDeviceOwner() -> false
            dhizukuPermissionGranted -> false
            controller.initDhizuku() && !dhizukuPermissionGranted -> true
            shizuku.isBinderAvailable() && !shizuku.isPermissionGranted() -> true
            else -> false
        }
        binding.authorizeButton.visibility = if (available) View.VISIBLE else View.GONE
        binding.authorizeButton.text = getString(R.string.authorize)
    }

    private fun authorize() {
        when {
            controller.initDhizuku() && !dhizukuPermissionGranted -> {
                dhizukuPermissionGranted = controller.isDhizukuPermissionGranted()
                if (!dhizukuPermissionGranted) {
                    controller.requestDhizukuPermission(object : DhizukuRequestPermissionListener() {
                        override fun onRequestPermission(grantResult: Int) {
                            dhizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                            refresh()
                        }
                    })
                }
            }
            shizuku.isBinderAvailable() && !shizuku.isPermissionGranted() -> {
                shizuku.requestPermission(shizukuRequestCode)
            }
            else -> toast(getString(R.string.no_authorization_available))
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            dhizukuPermissionGranted = false
            refresh()
        }

    private fun confirmSwitch(launcher: LauncherInfo) {
        when (val mode = activeMode()) {
            Mode.NONE -> {
                toast(getString(R.string.not_authorized_toast))
                return
            }
            Mode.DHIZUKU -> confirmWithDhizuku(launcher)
            Mode.SHIZUKU -> confirmWithShizuku(launcher)
            Mode.DEVICE_OWNER -> confirmWithDeviceOwner(launcher)
        }
    }

    private fun confirmWithDeviceOwner(launcher: LauncherInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_switch_title)
            .setMessage(getString(R.string.confirm_switch_message_device_owner, launcher.label))
            .setPositiveButton(R.string.confirm) { _, _ -> switchLauncher(launcher) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmWithDhizuku(launcher: LauncherInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_switch_title)
            .setMessage(getString(R.string.confirm_switch_message, launcher.label))
            .setPositiveButton(R.string.confirm) { _, _ ->
                switchLauncherViaDhizuku(launcher)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmWithShizuku(launcher: LauncherInfo) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_switch_title)
            .setMessage(getString(R.string.confirm_switch_message, launcher.label))
            .setPositiveButton(R.string.confirm) { _, _ ->
                switchLauncherViaShizuku(launcher)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun switchLauncher(launcher: LauncherInfo) {
        try {
            controller.setLauncherAsDeviceOwner(launcher.component)
            toast(getString(R.string.switch_success, launcher.label))
            refresh()
        } catch (e: Exception) {
            toast(getString(R.string.switch_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun switchLauncherViaDhizuku(launcher: LauncherInfo) {
        val result = controller.setLauncherAsDhizuku(launcher.component)
        if (result.isSuccess) {
            toast(getString(R.string.switch_success, launcher.label))
            refresh()
        } else {
            toast(getString(R.string.switch_failed, result.exceptionOrNull()?.message ?: "unknown"))
        }
    }

    private fun switchLauncherViaShizuku(launcher: LauncherInfo) {
        val component = launcher.component.flattenToString()
        shizuku.setHomeActivity(component) { ok, msg ->
            if (ok) {
                toast(getString(R.string.switch_success, launcher.label))
            } else {
                toast(getString(R.string.switch_failed, msg))
            }
        }
    }

    private fun resetDefault() {
        when (val mode = activeMode()) {
            Mode.NONE -> {
                toast(getString(R.string.not_authorized_toast))
                return
            }
            Mode.DHIZUKU -> {
                val current = controller.currentLauncherComponent()
                if (current == null) {
                    toast(getString(R.string.reset_none))
                    return
                }
                val result = controller.clearLauncherAsDhizuku(current.packageName)
                if (result.isSuccess) {
                    toast(getString(R.string.reset_success))
                    refresh()
                } else {
                    toast(getString(R.string.switch_failed, result.exceptionOrNull()?.message ?: "unknown"))
                }
            }
            Mode.SHIZUKU -> {
                val current = controller.currentLauncherComponent()
                if (current == null) {
                    toast(getString(R.string.reset_none))
                    return
                }
                shizuku.setHomeActivity(current.flattenToString(), clear = true) { ok, msg ->
                    if (ok) {
                        toast(getString(R.string.reset_success))
                        refresh()
                    } else {
                        toast(getString(R.string.switch_failed, msg))
                    }
                }
            }
            Mode.DEVICE_OWNER -> {
                val current = controller.currentLauncherComponent()
                if (current == null) {
                    toast(getString(R.string.reset_none))
                    return
                }
                try {
                    controller.clearLauncherAsDeviceOwner(current.packageName)
                    toast(getString(R.string.reset_success))
                    refresh()
                } catch (e: Exception) {
                    toast(getString(R.string.switch_failed, e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun activeMode(): Mode = when {
        controller.isOwnDeviceOwner() -> Mode.DEVICE_OWNER
        dhizukuPermissionGranted -> Mode.DHIZUKU
        shizuku.isPermissionGranted() -> Mode.SHIZUKU
        else -> Mode.NONE
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showWelcomeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_message)
            .setPositiveButton(R.string.got_it) { _, _ -> refresh() }
            .setCancelable(false)
            .show()
    }

    enum class Mode { DEVICE_OWNER, DHIZUKU, SHIZUKU, NONE }

    private enum class ModeBadge(val labelRes: Int, val bgRes: Int, val textColorRes: Int?) {
        DEVICE_OWNER(R.string.badge_device_owner, R.color.badge_green, R.color.white),
        DHIZUKU(R.string.badge_dhizuku, R.color.badge_blue, R.color.white),
        SHIZUKU(R.string.badge_shizuku, R.color.badge_orange, R.color.white),
        NONE(0, 0, null)
    }

    companion object {
        private const val PREFS_NAME = "launcher_switcher_prefs"
        private const val KEY_WELCOME_SHOWN = "welcome_shown"
    }
}
