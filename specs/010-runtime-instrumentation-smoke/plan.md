# Implementation Plan: Runtime Instrumentation Smoke

## Scope

Add the smallest Android device-level validation for the runtime control plane. This is intentionally narrower than the daemon or scenario specs: it proves the runtime endpoint can be started inside a debug Android process and accepts MCP-facing HTTP requests with the required session token.

## Technical Approach

1. Enable instrumentation testing in `ai-debug-runtime`.
2. Add a JUnit4 Android test that starts `AiDebugRuntime` with port `0` so Android assigns an unused localhost port.
3. Use `HttpURLConnection` directly to avoid adding another client dependency to the smoke test.
4. Decode responses with the shared `ProtocolJson` models.
5. Verify ping, auth rejection, UTF-8 body handling, and one protected mutable endpoint.

## Validation

Run compile-level validation on every development machine:

```bash
./gradlew :ai-debug-runtime:assembleDebugAndroidTest
```

Run device validation when an emulator or device is connected:

```bash
./gradlew :ai-debug-runtime:connectedDebugAndroidTest
```

## Risks

- Device tests are environment-dependent, so the required CI gate is compile-level until a stable emulator runner is available.
- The runtime singleton can leak endpoint state across tests; the smoke test must call `AiDebugRuntime.stop()` before and after execution.
