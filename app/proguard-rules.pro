# Add project specific ProGuard rules here.
-keep class com.hxy132465.launcherswitcher.MyDeviceAdminReceiver { *; }
-keep class com.hxy132465.launcherswitcher.ShizukuUserService { *; }
-keep class com.hxy132465.launcherswitcher.IUserService { *; }
-keep class com.hxy132465.launcherswitcher.IUserService$Stub { *; }
-keep class com.hxy132465.launcherswitcher.IUserService$Default { *; }
-dontwarn android.app.admin.IDevicePolicyManager**
-dontwarn com.rosan.dhizuku.**
-dontwarn rikka.shizuku.**
