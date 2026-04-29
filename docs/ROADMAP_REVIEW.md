# Roadmap Review

Last reviewed: 2026-04-24

## Verdict

The roadmap is aligned with the current product intent: build an AI debugging extension for Android debug apps with source access. It correctly avoids spending first-party effort on generic UI automation and focuses on network mutation, state control, compile-time probes, eval, object search, and memory-level inspection.

## Changes Applied During Review

- Removed wording that implied eval/object search were optional or separate.
- Updated capability groups to include `debug.*` as first-class AI-callable tools.
- Clarified that deep instrumentation, not power-mode terminology, is the long-term extension path.

## Review Findings

### R1. MVP Needs Two Rings

The first public MVP now includes network control, state control, storage adapters, object search, and eval. That is the right product direction, but implementation should still have two rings:

- **MVP-0 internal spike**: daemon/runtime connection, OkHttp mutation, one state, one object search, one eval path.
- **MVP-1 public alpha**: hardened schemas, audit, snapshot/restore, report export, release safety checks.

This keeps the product promise broad without forcing all safety and compatibility work into the first spike.

### R2. Eval Needs An Execution Strategy Spec

The roadmap says `debug.eval` and snippets are first-class tools. The spec must decide how they execute:

- interpreted expression DSL,
- Kotlin/Java snippet compiled to dex and loaded by app runtime,
- JVM debug-agent/JVMTI path,
- Frida/native-agent path,
- or a staged combination.

The initial spec should choose a practical first implementation and reserve the rest for later.

### R3. Object Search Needs Boundaries

Full heap search is expensive and platform-sensitive. The initial object search should be selected-package and registry-assisted:

- instrument constructors or lifecycle owners where useful,
- track known app singletons, ViewModels, repositories, stores, and DI bindings,
- support reflection-based field reads for selected packages,
- defer whole-heap/native memory search.

### R4. Contracts Should Lead Implementation

MCP tool contracts should be written before code for:

- tool names,
- request and response JSON,
- error codes,
- audit events,
- snapshot/restore behavior,
- mutability policy.

This is especially important because AI agents will call these tools directly.

### R5. Sample App Is A Required Test Fixture

The roadmap should treat a sample Android app as a conformance fixture, not a demo nice-to-have. It should include:

- OkHttp/Retrofit profile endpoint,
- Room table,
- SharedPreferences setting,
- DataStore preference,
- Hilt or Koin binding,
- a VIP/checkout branch,
- a method and field suitable for probe/hook/eval validation.

## Suggested Spec Split

- `001-runtime-mcp-control-plane`: daemon/runtime transport, sessions, capability discovery, audit.
- `002-network-control`: OkHttp/Retrofit history, mock, mutation, assertion.
- `003-state-storage-override`: typed state, storage adapters, DI overrides.
- `004-dynamic-debugging`: object search, eval, snippet runner, field/method probes.
- `005-gradle-debug-integration`: Gradle plugin, runtime/no-op dependency wiring, KSP/ASM, release checks.

This split follows the Spec Kit pattern of one feature directory per independently testable capability.
