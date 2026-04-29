# Quickstart: Runtime Instrumentation Smoke

Compile the instrumentation test APK:

```bash
./gradlew :ai-debug-runtime:assembleDebugAndroidTest
```

Run on a connected API 26+ emulator or device:

```bash
adb devices
./gradlew :ai-debug-runtime:connectedDebugAndroidTest
```

The smoke test validates the runtime endpoint directly and does not require the daemon, sample app, UIAutomator, or a scenario file.
