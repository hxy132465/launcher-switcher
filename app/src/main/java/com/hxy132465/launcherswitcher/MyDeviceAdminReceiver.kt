package com.hxy132465.launcherswitcher

import android.app.admin.DeviceAdminReceiver

/**
 * Minimal DeviceAdminReceiver. Its only purpose is to give the app a
 * ComponentName that can be promoted to Device Owner with:
 *
 *   adb shell dpm set-device-owner \
 *       com.hxy132465.launcherswitcher/.MyDeviceAdminReceiver
 *
 * Device Owner status is what grants access to
 * [android.app.admin.DevicePolicyManager.addPersistentPreferredActivity],
 * which is how the default launcher is switched without user confirmation.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver()
