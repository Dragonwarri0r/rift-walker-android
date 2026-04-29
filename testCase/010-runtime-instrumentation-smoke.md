# 010 Runtime Instrumentation Smoke Agent Test

## Goal

Verify the Android runtime endpoint inside a real instrumented test process:
socket startup, token boundary, UTF-8 request parsing, and mutable endpoint routing.

## Agent Flow

1. Compile the instrumentation APK.

```bash
./gradlew :ai-debug-runtime:assembleDebugAndroidTest
```

Expected evidence:

- Android test APK compiles.

2. Run on a connected API 26+ device or emulator.

```bash
./gradlew :ai-debug-runtime:connectedDebugAndroidTest
```

Expected evidence from `RuntimeEndpointInstrumentedTest`:

- `GET /runtime/ping` returns HTTP 200.
- Response includes non-empty `sessionId` and `sessionToken`.
- `debuggable == true`.
- Protected endpoint without token returns HTTP 401.
- `POST /capabilities/list` succeeds with UTF-8 JSON body such as `{"kind":"all","query":"会员🚀"}`.
- `POST /network/clearRules` returns JSON with a valid token.

3. Confirm cleanup behavior.

Expected evidence:

- The test stops the runtime in `@After` or `finally`.
- A repeated run does not fail from port collision.

## Failure Triage

- Port collision: test did not use `port = 0` or did not stop a previous endpoint.
- UTF-8 body fails: request body length is being interpreted as characters instead of bytes.
- Missing 401: token boundary is broken for protected endpoints.
