# Add project specific ProGuard rules here.
-keep class com.example.launcherswitcher.MyDeviceAdminReceiver { *; }
-keep class com.example.launcherswitcher.ShizukuUserService { *; }
-dontwarn android.app.admin.IDevicePolicyManager**
-dontwarn com.rosan.dhizuku.**
