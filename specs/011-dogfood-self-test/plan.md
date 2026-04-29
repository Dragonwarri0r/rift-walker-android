# Implementation Plan: Dogfood Self-Test Flow

## Scope

Create one developer-facing vertical slice that exercises the existing runtime and daemon tools end to end against the sample app.

## Technical Approach

1. Add protocol models for app/ADB tools and runtime readiness polling.
2. Register safe ADB-backed daemon tools:
   - `device.list`
   - `adb.forward`
   - `app.forceStop`
   - `app.launch`
3. Add daemon-side `runtime.waitForPing`, implemented as polling `runtime.ping`.
4. Extend `network.assertCalled` with `timeoutMs` and `pollIntervalMs`.
5. Add a built-in `DogfoodScenarios.sampleProfileBranch(...)` scenario.
6. Add a `dogfood-sample` daemon CLI command that runs the scenario and writes a report.
7. Add `scripts/spec011-dogfood.sh` for build/install/run validation.

## Validation

```bash
./gradlew :ai-debug-daemon:test :ai-debug-runtime:testDebugUnitTest
./gradlew :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety
scripts/spec011-dogfood.sh
```

## Risks

- Device availability remains environment-dependent.
- The sample profile fetch is asynchronous; `network.assertCalled.timeoutMs` avoids turning that into a fixed sleep.
