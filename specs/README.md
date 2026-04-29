# Specs Index

This repository follows a lightweight Spec Kit style:

```text
.specify/memory/constitution.md
specs/NNN-feature/
  spec.md
  plan.md
  contracts/
  tasks.md
```

Feature specs are intentionally small enough for AI agents to use during implementation while still carrying acceptance criteria and tool contracts.

## Feature Map

| Spec | Purpose | Priority |
| --- | --- | --- |
| `001-runtime-mcp-control-plane` | Connect local MCP daemon to debug app runtime, manage sessions, expose capabilities and audit logs | P0 |
| `002-network-control` | Record, mock, mutate, delay, fail, replay, and assert app network calls | P0 |
| `003-state-storage-override` | Expose typed app state, storage adapters, snapshot/restore, and DI overrides | P1 |
| `004-dynamic-debugging` | Provide AI-callable object search, eval, snippets, probes, hooks, and memory inspection | P1 |
| `005-gradle-debug-integration` | Provide Gradle plugin, debug/no-op dependency wiring, codegen, ASM instrumentation, and release checks | P1 |
| `006-scenario-report-runner` | Compose runtime tools into reproducible AI scenarios and JSON report artifacts | P1 |
| `007-graphql-grpc-network` | Match GraphQL operations and gRPC service/method calls in network control | P1 |
| `008-network-record-to-mock` | Convert captured network history records into reusable static mock rules | P1 |
| `009-gradle-hook-e2e` | Verify Gradle-inserted hook/trace bytecode and runtime hook bridge resolution | P1 |
| `010-runtime-instrumentation-smoke` | Validate runtime localhost endpoint, token auth, and UTF-8 request parsing on Android | P1 |
| `011-dogfood-self-test` | Run a sample AI self-test loop across app launch, runtime, network mock, state, hook, cleanup, and report | P1 |
| `012-media-input-foundation` | Provide shared media target discovery, fixture staging, runtime foundation, MCP fixture tools, and release-safety baselines | P2 |
| `013-audio-input-control` | Inject WAV/PCM fixtures into `AudioRecord.read(...)` through debug-only ASM call-site rewrite and runtime bridge controls | P2 |
| `014-camera-input-control` | Inject image fixtures through CameraX analyzers, ML Kit `InputImage` factories, and configured custom frame hooks | P2 |
| `015-media-input-verification-suite` | Verify audio and camera media input control end-to-end with sample triggers, fixtures, reports, cleanup, and release safety | P1 |

## Implementation Guidance

Start with specs `001` and `002` for the internal spike. Add the smallest vertical slice of `003` and `004` before calling the project an AI debugging extension, because network-only behavior is not differentiated enough.

Media input control is split into three specs to keep implementation tasks clear without over-fragmenting the roadmap:

- `012` owns shared foundation: fixture staging, target discovery, common protocol, MCP fixture tools, and release-safety baselines.
- `013` owns AudioRecord rewriting and audio fixture consumption.
- `014` owns CameraX, ML Kit, and configured custom frame hooks.
- `015` owns proof: connected-device verification, sample media dogfood triggers, reports, and clear pass/blocked/fail evidence for `013` and `014`.
