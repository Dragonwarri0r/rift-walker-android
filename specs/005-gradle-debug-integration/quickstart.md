# Quickstart: Gradle Debug Integration

## Apply The Plugin

```kotlin
plugins {
    id("com.android.application")
    id("com.riftwalker.ai-debug")
    kotlin("android")
}

aiDebug {
    enabledForBuildTypes.set(listOf("debug"))
    probePackage("com.example.app")
    exposeField("com.example.session.UserSession", "currentUser")
    traceMethod("com.example.checkout.CheckoutRepository#quote()")
    overrideMethod("com.example.flags.FeatureFlags#isEnabled(java.lang.String)")
}
```

For this repository, the plugin defaults to project dependencies:

```text
debugImplementation(project(":ai-debug-runtime"))
releaseImplementation(project(":ai-debug-runtime-noop"))
implementation(project(":ai-debug-annotations"))
```

Published users can override `runtimeDependency`, `noopDependency`, and `annotationsDependency`.

## Annotate Source

```kotlin
object DebugCapabilities {
    @AiState(path = "user.isVip", type = "boolean", description = "VIP branch")
    var isVip: Boolean = false

    @AiAction(path = "sample.refresh")
    fun refresh() = Unit
}
```

The generated registry is emitted to:

```text
build/generated/source/aiDebug/main/com/riftwalker/aidebug/generated/AiDebugGeneratedRegistry.kt
```

`AiDebugRuntime.start()` loads this registry if it exists.

## ASM Hooks

Configured `traceMethod` targets emit `trace.enter`, `trace.exit`, and `trace.throw` audit events in enabled debug build types.
Configured `overrideMethod` targets currently support no-argument `Boolean` and `String` JVM returns through `hook.overrideReturn` / `hook.throw`.

## Build Artifacts

```bash
./gradlew :sample-app:generateAiDebugArtifacts
```

Outputs:

```text
sample-app/build/ai-debug/debug-capabilities.json
sample-app/build/ai-debug/probe-symbol-index.json
sample-app/build/ai-debug/release-safety-report.json
```
