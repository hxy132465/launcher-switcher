# Add project specific ProGuard rules here.
-keep class com.example.launcherswitcher.MyDeviceAdminReceiver { *; }
-keep class com.example.launcherswitcher.ShizukuUserService { *; }
-keep class com.example.launcherswitcher.IUserService { *; }
-keep class com.example.launcherswitcher.IUserService$Stub { *; }
-keep class com.example.launcherswitcher.IUserService$Default { *; }
-dontwarn android.app.admin.IDevicePolicyManager**
-dontwarn com.rosan.dhizuku.**
-dontwarn rikka.shizuku.**
