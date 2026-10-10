// AIDL interface implemented by ShizukuUserService, which runs in a separate process with the
// identity of shell (ADB) or root. Return values are prefixed with "EXIT:<code>\n" so the
// caller can distinguish success (0) from failure (non-zero) reliably, independent of output text.
package com.example.launcherswitcher;

interface IUserService {

    // Reserved method used by the Shizuku server to destroy the user service.
    void destroy() = 16777114;

    void exit() = 1;

    // Sets the default home activity, e.g. "com.example.launcher/.LauncherActivity".
    // Returns "EXIT:<exitCode>\n<output>".
    String setHomeActivity(String component) = 2;

    // Clears the persistent home preference for the package part of [component].
    // Returns "EXIT:<exitCode>\n<output>".
    String clearHomeActivity(String component) = 3;
}
