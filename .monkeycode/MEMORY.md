# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
User instruction entries should follow this format:

[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
Entries discovered by the Agent during task execution should follow this format:

[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-10-09
- Context: Discovered by Agent while scaffolding an Android (Gradle) project in this workspace
- Category: Environment Configuration
- Instructions:
  - The execution environment has no Java, Gradle, or Android SDK installed (`java`/`gradle`/`adb` not found, `ANDROID_HOME` empty). Android projects cannot be compiled or run here; they must be built in Android Studio or an environment with the Android SDK.
  - This workspace is a generic project template (empty except `.gitignore`); each task may introduce a different tech stack.

[Project Knowledge Summary]
- Date: 2026-10-09
- Context: Discovered by Agent while building the LauncherSwitcher Android app
- Category: Build Methods
- Instructions:
  - Use JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`) with Gradle 8.7 and Android SDK at `/opt/android-sdk`. Set `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` and prepend `JAVA_HOME/bin` + `/opt/gradle-8.7/bin` to `PATH`.
  - The Android SDK command-line tools are at `/opt/android-sdk/cmdline-tools/latest`; use `sdkmanager --sdk_root=/opt/android-sdk` to install `platforms;android-34`, `build-tools;34.0.0`, and `platform-tools`.
  - Dhizuku-API 2.6.0 mandates compileSdk 37, which conflicts with AGP 8.5.2. Use Dhizuku-API `2.5.4` (minCompileSdk=1) instead.
  - Lint rule `SoonBlockedPrivateApi` fires on reflection access to `DevicePolicyManager.mService` (intentional for Dhizuku proxy). Suppress with `@SuppressLint("SoonBlockedPrivateApi")` on the method.

[Project Knowledge Summary]
- Date: 2026-10-09
- Context: Discovered by Agent while integrating Shizuku/Dhizuku into the LauncherSwitcher app
- Category: Troubleshooting & Debugging
- Instructions:
  - Shizuku API `13.1.5` removed `Shizuku.newProcess()` (made it `private`). Use `Shizuku.bindUserService(UserServiceArgs, ServiceConnection)` instead, which requires an AIDL interface and a `destroy()` method that calls `System.exit(0)`.
  - Dhizuku's `DhizukuRequestPermissionListener` is an abstract class (`extends IDhizukuRequestPermissionListener.Stub`), not an interface. In Kotlin, instantiate with `object : DhizukuRequestPermissionListener() { ... }` (note the `()`).
  - Dhizuku-API `2.6.0` requires compileSdk 37; older `2.5.4` works with compileSdk 34.
  - `kotlin.Result.Success` / `Failure` are `internal` classes in Kotlin stdlib. Do not pattern-match on them; instead use `result.isSuccess` / `result.isFailure` / `result.exceptionOrNull()` to inspect results.
  - GitHub PAT pushed via `https://user:TOKEN@github.com/...` may fail with `credential helper: server returned status 500`. Use the token inline for the push, then strip it from the remote URL afterward.
  - `ShizukuHelper.setHomeActivity()` must post to `mainHandler` before accessing `pendingQueue`; `drainPending()` called from `onServiceConnected` may fire on a Binder thread — always dispatch through `mainHandler.post` to avoid data races on `pendingQueue` (ConcurrentLinkedQueue is used).
  - `ShizukuUserService.exec()` must be `@Synchronized` (or use `AtomicBoolean` lock) to prevent concurrent shell commands from interleaving when Shizuku's Binder thread pool dispatches multiple calls.
  - AIDL return strings from `ShizukuUserService` should use `EXIT:<code>\n<output>` prefix so callers can reliably detect success via exit code rather than text heuristics.
  - `LauncherController.setLauncherAsDeviceOwner()` and `clearLauncherAsDeviceOwner()` must return `Result<Unit>` (not `Unit`) so failures surface as `Result.failure()` instead of crashing the app.
  - `LauncherController.initDhizuku()` is expensive (Binder call); cache its result in `MainActivity` to avoid repeated calls across `refresh()`/`updateAuthorizeButton()`/`authorize()`.
  - `dpm set-device-owner` requires **root** (uid 0) on Android; ADB shell (uid 2000) cannot execute it. Shizuku running as root (Magisk/KernelSU) can succeed; as ADB shell it fails with "Permission denied". Detect mode with `Shizuku.getUid() == 0`.
  - When Shizuku set-device-owner fails in shell mode, show a clear error message guiding the user to either use root-mode Shizuku or fall back to manual ADB command.
