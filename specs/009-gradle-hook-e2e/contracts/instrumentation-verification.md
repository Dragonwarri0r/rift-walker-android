# Gradle Contract: Instrumentation Verification

## Task

For build type `debug`, the plugin registers:

```text
verifyAiDebugDebugInstrumentation
```

## Inputs

```text
traceMethods
overrideMethods
build/intermediates/classes/debug/transformDebugClassesWithAsm/dirs
build/intermediates/classes/debug/transformDebugClassesWithAsm/jars
```

## Output

```text
build/ai-debug/instrumentation-debug-report.json
```

Example:

```json
{
  "buildType": "debug",
  "verifiedAtEpochMs": 1770000000000,
  "traceMethods": [
    {"methodId": "com.example.MainActivity#render()", "className": "com.example.MainActivity", "verified": true}
  ],
  "overrideMethods": [
    {"methodId": "com.example.Flags#enabled()", "className": "com.example.Flags", "verified": true}
  ]
}
```

## Failure

If a configured method is missing the expected bridge call, the task fails with the method id and expected bridge type.
