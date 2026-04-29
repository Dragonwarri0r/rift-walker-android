# Quickstart: Runtime MCP Control Plane

This validates the first vertical slice:

1. Build the sample app.
2. Install and start it on a connected emulator/device.
3. Create a host-to-app ADB tunnel with `adb forward`.
4. Call the daemon `runtime.ping` and `capabilities.list` commands.

## One Command

```bash
scripts/spec01-smoke.sh
```

Optional environment variables:

```bash
DEVICE_SERIAL=emulator-5554 HOST_PORT=37913 DEVICE_PORT=37913 scripts/spec01-smoke.sh
```

## Manual Commands

```bash
./gradlew :sample-app:assembleDebug
adb install -r sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb shell am start -n com.riftwalker.sample/.MainActivity
adb forward tcp:37913 tcp:37913
./gradlew -q :ai-debug-daemon:run --args="ping --host-port=37913"
./gradlew -q :ai-debug-daemon:run --args="capabilities --host-port=37913"
```

Expected `runtime.ping` shape:

```json
{
  "packageName": "com.riftwalker.sample",
  "processId": 12345,
  "debuggable": true,
  "apiLevel": 35,
  "runtimeVersion": "0.1.0",
  "sessionId": "session_..."
}
```
