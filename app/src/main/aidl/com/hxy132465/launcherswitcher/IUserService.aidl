// AIDL interface implemented by ShizukuUserService, which runs in a separate process with the
// identity of shell (ADB) or root, launched by the Shizuku server.
package com.hxy132465.launcherswitcher;

interface IUserService {

    // Reserved method used by the Shizuku server to destroy the user service.
    void destroy() = 16777114;

    void exit() = 1;

    // Sets the default home activity, e.g. "com.example.launcher/.LauncherActivity".
    // Returns "EXIT:<code>\n<output>".
    String setHomeActivity(String component) = 2;

    // Clears the persistent home preference for the package part of [component].
    // Returns "EXIT:<code>\n<output>".
    String clearHomeActivity(String component) = 3;

    // Attempts to set this app as the device owner via `dpm set-device-owner`.
    // Only succeeds when Shizuku is running with ROOT identity (Magisk/KernelSU).
    // Returns "EXIT:<code>\n<output>".
    String setDeviceOwner() = 4;

    // Returns the UID of the current process so the caller can determine
    // whether Shizuku is running as root (0) or shell (2000).
    int getUid() = 5;
}
