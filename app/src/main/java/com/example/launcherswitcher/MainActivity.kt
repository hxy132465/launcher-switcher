package com.example.launcherswitcher

import android.content.ComponentName
import android.content.pm.PackageManager
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

    private val shizukuRequestCode = 1
    private val dhizukuRequestCode = 2

    @Volatile
    private var dhizukuPermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        controller = LauncherController(this)
        shizuku = ShizukuHelper(this)

        binding.launcherList.layoutManager = LinearLayoutManager(this)
        binding.launcherList.adapter = LauncherAdapter(emptyList(), null) { confirmSwitch(it) }

        binding.refreshButton.setOnClickListener { refresh() }
        binding.authorizeButton.setOnClickListener { authorize() }
        binding.resetButton.setOnClickListener { resetDefault() }
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

        val current = controller.currentLauncherComponent()
        binding.currentLauncher.text = getString(
            R.string.current_launcher_format,
            current?.packageName ?: getString(android.R.string.unknownName)
        )

        dhizukuPermissionGranted = controller.initDhizuku() && controller.isDhizukuPermissionGranted()

        val launchers = controller.installedLaunchers()
        binding.launcherList.swapAdapter(
            LauncherAdapter(launchers, current?.flattenToString()) { confirmSwitch(it) },
            true
        )

        updateAuthorizeButton()
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

    enum class Mode { DEVICE_OWNER, DHIZUKU, SHIZUKU, NONE }
}
