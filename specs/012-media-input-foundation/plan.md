# Implementation Plan: Media Input Foundation

## Scope

Build the shared infrastructure for business-transparent media input control. This spec owns discovery, fixture staging, shared protocol, runtime foundation, MCP fixture tools, Gradle DSL root, and baseline release safety. Audio and camera substitution are implemented by follow-up specs that depend on this foundation.

## Technical Approach

1. Add shared protocol models:
   - `MediaTarget`,
   - `MediaTargetListRequest/Response`,
   - `MediaFixture`,
   - fixture register/list/delete requests,
   - `MediaCapabilitiesResponse`,
   - shared history/assertion base shapes.
2. Add runtime foundation:
   - `MediaTargetRegistry`,
   - `MediaFixtureStore`,
   - `MediaController`,
   - shared bridge registration APIs,
   - built-in media capabilities.
3. Add daemon fixture staging:
   - host path input,
   - sha256 calculation,
   - ADB selected-device staging under `/data/local/tmp/ai-debug/fixtures`,
   - runtime registration with device path and metadata.
4. Add MCP and runtime HTTP tools:
   - `media.capabilities`,
   - `media.targets.list`,
   - `media.fixture.register`,
   - `media.fixture.list`,
   - `media.fixture.delete`.
5. Add Gradle plugin shared DSL root:
   - `mediaInputControl { enabledForDebugOnly.set(true) }`,
   - nested sections reserved for audio and camera specs.
6. Extend release safety:
   - forbid media foundation runtime classes,
   - forbid fixture staging code in release artifacts,
   - report media-specific leak kinds in `release-safety-report.json`.

## Implementation Slices

### Slice A - Protocol First

Define the media foundation models in `ai-debug-protocol` before runtime or daemon work. This keeps later audio and camera specs from inventing incompatible target, fixture, and history shapes.

### Slice B - Runtime Registry And Fixture Store

Implement target discovery and fixture metadata storage inside `ai-debug-runtime`. This slice should be unit-testable without ADB by registering synthetic bridge hits and local fixture metadata.

### Slice C - Daemon Staging And MCP

Add daemon-side host file staging only after the runtime fixture registration path is stable. This slice owns ADB push, sha256 calculation, and runtime registration.

### Slice D - Gradle DSL And Release Baseline

Add the shared `mediaInputControl` DSL root and baseline release scanner rules, but do not enable audio/camera rewriting in this spec. `013` and `014` own concrete visitors.

### Slice E - Foundation Smoke

Create a smoke script that registers a fixture, lists fixtures, registers or observes a synthetic target, and verifies release safety. This proves the foundation before media-specific hooks are implemented.

## Ownership

- `ai-debug-protocol`: shared media models.
- `ai-debug-runtime`: media controller, target registry, fixture store, capability descriptors, HTTP routes.
- `ai-debug-daemon`: fixture staging and MCP tool registration.
- `ai-debug-gradle-plugin`: shared DSL root and release-safety defaults.
- `scripts`: foundation smoke and negative release leak checks.

## Validation

```bash
./gradlew :ai-debug-protocol:test :ai-debug-daemon:test
./gradlew :ai-debug-runtime:testDebugUnitTest
./gradlew :sample-app:checkAiDebugReleaseSafety
scripts/spec012-media-foundation-smoke.sh
scripts/spec012-negative-media-foundation-release-leak.sh
```

## Dependencies

- Builds on `001-runtime-mcp-control-plane` for runtime HTTP, session auth, capabilities, audit, and cleanup.
- Builds on `005-gradle-debug-integration` for plugin DSL and release safety task extension.
- Enables `013-audio-input-control` and `014-camera-input-control`.

## Risks

- Target ids must be stable enough for repeatable scenarios but specific enough to avoid collisions.
- Fixture staging paths must be scoped and cleaned up to avoid stale files on shared devices.
- The foundation must avoid pulling audio/camera SDK dependencies into release or modules that do not use them.
