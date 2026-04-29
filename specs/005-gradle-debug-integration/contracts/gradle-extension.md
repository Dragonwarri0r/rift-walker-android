# Gradle Extension Contract

```kotlin
aiDebug {
    enabledForBuildTypes.set(listOf("debug"))
    runtimeDependency.set("com.riftwalker:ai-debug-runtime:<version>")
    noopDependency.set("com.riftwalker:ai-debug-runtime-noop:<version>")
    annotationsDependency.set("com.riftwalker:ai-debug-annotations:<version>")

    exportSchema.set(true)
    schemaOutput.set(layout.buildDirectory.file("ai-debug/debug-capabilities.json"))
    symbolIndexOutput.set(layout.buildDirectory.file("ai-debug/probe-symbol-index.json"))

    probePackage("com.example.app")
    exposeField("com.example.session.UserSession", "currentUser")
    traceMethod("com.example.checkout.CheckoutRepository.quote")
    overrideMethod("com.example.flags.FeatureFlags.isEnabled")

    releaseSafety {
        failOnRuntimeLeak.set(true)
        forbiddenClasses.add("com.riftwalker.aidebug.runtime.RuntimeHttpEndpoint")
        forbiddenClasses.add("com.riftwalker.aidebug.runtime.network.NetworkControlInterceptor")
        forbiddenManifestEntries.add("com.riftwalker.aidebug.DebugEndpoint")
    }
}
```

Generated artifacts:

```text
build/ai-debug/debug-capabilities.json
build/ai-debug/probe-symbol-index.json
build/ai-debug/release-safety-report.json
build/generated/source/aiDebug/main/com/riftwalker/aidebug/generated/AiDebugGeneratedRegistry.kt
```

MVP annotation support:

```kotlin
object DebugCapabilities {
    @AiState(path = "sample.annotatedVip", type = "boolean")
    var annotatedVip: Boolean = false

    @AiAction(path = "sample.generatedAction")
    fun generatedAction() = Unit
}
```

The generated registry is loaded by `AiDebugRuntime.start()` if present.
