package android.app.admin;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

/**
 * Compile-time stub for the hidden framework interface
 * {@code android.app.admin.IDevicePolicyManager}.
 *
 * <p>This module is consumed with {@code compileOnly}. The class is never packaged into the
 * APK: at runtime the framework's own {@code IDevicePolicyManager} (from the boot classpath)
 * is used. Only the members referenced by the app are declared here. The
 * {@link Stub#asInterface(IBinder)} call site is resolved against the real framework class at
 * runtime, which returns a proxy that performs the real (hidden) AIDL transaction.</p>
 *
 * <p>This is the exact technique used by the Dhizuku-API project to let a normal app issue
 * Device Owner operations through a Dhizuku binder wrapper.</p>
 */
public interface IDevicePolicyManager extends IInterface {

    abstract class Stub extends Binder implements IDevicePolicyManager {

        public static IDevicePolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException(
                    "This stub is compile-only; the framework implementation is used at runtime.");
        }
    }

    ComponentName getDeviceOwnerComponent(boolean callingUserOnly);
}
