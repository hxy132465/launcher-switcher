// AIDL interface implemented by ShizukuUserService, which runs in a separate process with the
// identity of shell (ADB) or root. Method that matters to us: setHomeActivity / clearHomeActivity.
package com.example.launcherswitcher;

interface IUserService {

    // Reserved method used by the Shizuku server to destroy the user service.
    void destroy() = 16777114;

    void exit() = 1;

    // Sets the default home activity, e.g. "com.example.launcher/.LauncherActivity".
    // Returns the combined stdout/stderr of the shell command.
    String setHomeActivity(String component) = 2;

    // Clears the persistent home preference for the package part of [component].
    String clearHomeActivity(String component) = 3;
}
