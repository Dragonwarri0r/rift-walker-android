# Implementation Plan: Gradle Debug Integration

**Branch**: `005-gradle-debug-integration`  
**Date**: 2026-04-24  
**Spec**: `specs/005-gradle-debug-integration/spec.md`

## Summary

Use source-build access to make integration ergonomic and powerful. The plugin should reduce manual wiring, generate capability registries, create symbol indexes, insert hook points, and prevent release leakage.

## Technical Context

| Area | Decision |
| --- | --- |
| Build system | Gradle plugin for Android app modules |
| AGP baseline | 8.12/8.13 first, AGP 9.1 follow-up |
| Codegen | KSP/KotlinPoet or equivalent |
| Instrumentation | AGP Instrumentation API + ASM |
| Release safety | Variant-aware artifact scanner |

## Example Configuration

```kotlin
plugins {
    id("com.riftwalker.ai-debug")
}

aiDebug {
    enabledForBuildTypes.set(listOf("debug"))
    probePackage("com.example.app")
    exposeField("com.example.session.UserSession", "currentUser")
    traceMethod("com.example.checkout.CheckoutRepository.quote")
    overrideMethod("com.example.flags.FeatureFlags.isEnabled")
    exportSchema.set(true)
}
```

## Implementation Notes

- Keep manual runtime APIs working without the plugin, so early specs can ship before full Gradle integration.
- Put all plugin-generated behavior behind variant checks.
- Make schema and symbol index artifacts easy for the daemon to locate.
- Avoid legacy Transform API.

## Risks

- AGP and Kotlin version combinations can be brittle. Start with a narrow supported matrix.
- ASM instrumentation must preserve Kotlin suspend functions, metadata, and incremental build behavior.
- Auto-inserting OkHttp interceptors across arbitrary builders may require staged support.
