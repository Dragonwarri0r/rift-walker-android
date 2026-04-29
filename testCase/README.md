# Agent-Oriented Test Cases

These test cases are written for AI agents that operate the runtime MCP tools directly.
They are intentionally more guiding than script-like: each case tells the agent what to discover,
which tools to call, what evidence to collect, and how to interpret failures.

## Shared Setup

Use this setup before any device-backed case unless the case says otherwise:

```bash
./gradlew :sample-app:assembleDebug
adb install -r sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb shell am force-stop com.riftwalker.sample || true
adb shell pm clear com.riftwalker.sample
adb shell am start -n com.riftwalker.sample/.MainActivity
adb forward tcp:37913 tcp:37913
```

Then start an MCP daemon session or call the runtime HTTP endpoint through a client that
adds the `X-Ai-Debug-Token` returned by `runtime.ping`.

## Agent Rules

- Start every case with `runtime.ping` and `capabilities.list`.
- Prefer `runtime.waitForPing` after launching the app; avoid fixed sleeps when a wait/assert tool exists.
- Record the important response fields, not only pass/fail text.
- After a mutating step, verify the mutation through an independent read or history tool.
- Always clean up rules, fixtures, hooks, overrides, and session state.
- Treat 401 and structured `error` responses as useful evidence, not just failures.
- For partial media specs, distinguish "tool contract works" from "real app call-site injection works".

## Index

| Case | Scope | Primary Evidence |
| --- | --- | --- |
| [004 Dynamic Debugging](004-dynamic-debugging.md) | object search, eval, probe, hook | object/probe result, audit events, cleanup |
| [005 Gradle Debug Integration](005-gradle-debug-integration.md) | debug wiring, generated capabilities, release safety | build artifacts, capability visibility, release report |
| [006 Scenario Report Runner](006-scenario-report-runner.md) | scenario orchestration and reports | run steps, stop/continue behavior, report JSON |
| [007 GraphQL/gRPC Network](007-graphql-grpc-network.md) | protocol-aware matching | matched rules, protocol metadata in history |
| [008 Network Record To Mock](008-network-record-to-mock.md) | capture-to-replay workflow | source record, generated mock, redacted body |
| [009 Gradle Hook E2E](009-gradle-hook-e2e.md) | ASM hook/trace verification | verification task output, hook effect |
| [010 Runtime Instrumentation Smoke](010-runtime-instrumentation-smoke.md) | Android endpoint smoke | instrumentation result, token boundary, UTF-8 body |
| [011 Dogfood Self-Test](011-dogfood-self-test.md) | whole sample loop | dogfood scenario report and cleanup |
| [012 Media Foundation](012-media-input-foundation.md) | media target and fixture foundation | capabilities, staged fixture, target list |
| [013 Audio Input Control](013-audio-input-control.md) | AudioRecord fixture injection | audio target, history, assertConsumed |
| [014 Camera Input Control](014-camera-input-control.md) | CameraX/ML Kit fixture injection | camera target, snapshot, assertConsumed |
| [015 Media Input Verification Suite](015-media-input-verification-suite.md) | 013/014 connected-device proof | `scripts/spec015-media-input-verification.sh`, report JSON |

## Baseline Regression

Before running the newer cases, run the completed baseline verification:

```bash
./gradlew test :sample-app:assembleDebug
bash scripts/completed-specs-verify.sh
```

If the baseline fails, fix it first. Later cases depend on runtime auth, network history,
state, storage, override, and audit behavior from specs 001-003.
