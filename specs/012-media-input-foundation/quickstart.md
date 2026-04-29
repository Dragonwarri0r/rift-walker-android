# Quickstart: Media Input Foundation

This feature is not implemented yet. The target workflow below defines how shared media fixture staging and target discovery should work before audio or camera injection is added.

## Start Runtime

```bash
./gradlew :sample-app:assembleDebug
adb install -r sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb shell am start -n com.riftwalker.sample/.MainActivity
adb forward tcp:37913 tcp:37913
```

## Start MCP Daemon

```bash
./gradlew -q :ai-debug-daemon:run --args="mcp --host-port=37913"
```

## Register A Fixture

```json
{
  "tool": "media.fixture.register",
  "arguments": {
    "fixtureId": "sample-audio",
    "hostPath": "/absolute/path/fixtures/sample.wav",
    "mimeType": "audio/wav",
    "metadata": {
      "sampleRateHz": 16000,
      "channelCount": 1,
      "encoding": "pcm_16bit"
    }
  }
}
```

The daemon should push the file to the connected device, calculate sha256, and register the runtime fixture by device path.

## List Fixtures

```json
{
  "tool": "media.fixture.list",
  "arguments": {
    "mimePrefix": "audio/"
  }
}
```

## List Targets

```json
{
  "tool": "media.targets.list",
  "arguments": {
    "includeObserved": true
  }
}
```

Before audio/camera specs are implemented, this can be validated with a synthetic runtime target registration test. After `013` and `014`, it should return real AudioRecord, CameraX, and ML Kit targets.
