# Implementation Plan: Gradle Hook E2E

## Summary

The ASM hook insertion already exists for Boolean and String method returns. This spec turns it into an auditable build contract by adding a verification task and runtime bridge tests.

## Technical Context

| Area | Decision |
| --- | --- |
| ASM bridge | Reuse `AiDebugHookBridge` |
| Runtime hook store | Reuse `DynamicDebugController` / `HookStore` |
| Build verification | Scan transformed `.class` files and jars for method ids and bridge references |
| Sample target | `MainActivity#isNewCheckoutEnabled()` hook and `MainActivity#renderLocalState()` trace |
| Device dependency | None for this spec; bytecode and JVM runtime bridge smoke tests are enough |

## Flow

1. Gradle plugin instruments enabled debug variants.
2. `verifyAiDebug<Variant>Instrumentation` scans `intermediates/classes/<variant>/transform<Variant>ClassesWithAsm`.
3. The task fails if configured trace/override method ids cannot be found with the expected bridge call.
4. Runtime unit tests validate bridge return and throw behavior.

## Follow-Ups

- Support primitive return types beyond Boolean/String.
- Pass method arguments from ASM into hook resolution.
- Add Android instrumentation smoke for real Activity UI state override.
