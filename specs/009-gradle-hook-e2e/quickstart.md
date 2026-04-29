# Quickstart: Gradle Hook E2E

1. Configure a sample hook and trace:

```kotlin
aiDebug {
    overrideMethod("com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()")
    traceMethod("com.riftwalker.sample.MainActivity#renderLocalState()")
}
```

2. Build and verify:

```bash
./gradlew :sample-app:assembleDebug :sample-app:verifyAiDebugDebugInstrumentation
```

3. Install a hook rule through MCP:

```json
{
  "tool": "hook.overrideReturn",
  "arguments": {
    "methodId": "com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()",
    "returnValue": true,
    "times": 1
  }
}
```

4. Trigger the method in the app. The debug bridge returns the override before the original method body runs.
