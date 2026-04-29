# 009 Gradle Hook E2E Agent Test

## Goal

Verify that configured Gradle instrumentation inserts hook/trace bridges and runtime hook rules
change the behavior of instrumented debug code.

## Agent Flow

1. Build debug and run instrumentation verification.

```bash
./gradlew :sample-app:assembleDebug :sample-app:verifyAiDebugDebugInstrumentation
```

If the exact task name differs by module, discover it:

```bash
./gradlew tasks --all | rg 'verifyAiDebug.*Instrumentation'
```

Expected evidence:

- The verification task passes.
- `build/ai-debug/instrumentation-debug-report.json` exists for the relevant module.
- The report marks configured trace and override methods as `verified: true`.

2. Inspect the report.

Check that each configured method has:

- `methodId`
- `className`
- `verified == true`
- expected bridge type: trace or hook

3. Install a return override.

```json
{
  "tool": "hook.overrideReturn",
  "arguments": {
    "methodId": "<configured no-arg Boolean or String method id>",
    "returnValue": true,
    "times": 1
  }
}
```

Trigger the app path that calls the method.

Expected evidence:

- App behavior reflects the overridden value.
- `audit.history` contains `hook.overrideReturn`.
- If hook history exists, it records one consume.

4. Install a throw override.

```json
{
  "tool": "hook.throw",
  "arguments": {
    "methodId": "<configured method id>",
    "message": "Injected failure",
    "times": 1
  }
}
```

Expected evidence:

- App path receives the injected exception or error branch.
- The failure is audit-visible and cleanup-safe.

5. Cleanup.

```json
{"tool":"hook.clear","arguments":{"hookId":"<hookId>"}}
{"tool":"session.cleanup","arguments":{}}
```

## Negative Checks

- Remove or misconfigure an expected method in a temporary branch and confirm
  `:sample-app:verifyAiDebugDebugInstrumentation` fails with the missing method id.
- Build release and confirm no hook bridge/runtime debug classes leak.

## Failure Triage

- Runtime hook installs but app behavior does not change: build-time instrumentation did not hit that method.
- Verification passes but runtime fails: check method id normalization between Gradle and runtime.
- Release safety fails: debug bridge classes leaked into release output.
