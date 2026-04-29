# AI Runtime Testbench Roadmap

Last updated: 2026-04-29

## 1. Product Direction

This project is an AI-facing runtime control plane for Android debug builds.

The target app is assumed to be:

- Built from source.
- Installed as a debug or internal test build.
- Allowed to integrate a Gradle plugin, AAR, annotations, and compile-time instrumentation.
- Tested on modern devices or emulators. The preferred test fleet is Android 12+.

The product should help AI coding agents cover runtime branches that are hard to reach by only clicking UI or reading logs:

- Change API responses without editing backend mocks and rebuilding the app.
- Switch user, feature, payment, cache, time, and remote-config states.
- Trace branch decisions and repository/use-case calls.
- Override selected method returns or field values in debug builds.
- Inject deterministic audio and camera inputs into debug builds without requiring business source changes.
- Produce reproducible debug actions that a developer can inspect and rerun.

The first version is not a general UI automation tool. UI control, screenshots, and layout trees can be delegated to tools such as UIAutomator, Maestro, Appium, or DTA-like integrations. This project focuses on network control, typed state control, dependency override, compile-time probes, and AI-friendly MCP tools.

## 2. Positioning

Working name:

> AI Runtime Override Plane for Android debug builds

One-line description:

> A compile-time-instrumented Android debug runtime that lets AI agents mock network responses, mutate typed app state, trace business execution, and override selected runtime behavior through MCP.

Core principles:

- Typed: exposed states and overrides should have schemas, types, descriptions, and constraints.
- Reversible: every mutation should support snapshot, restore, audit, and cleanup.
- Semantic: AI should be able to choose the right level of control for the case: business capabilities, stable probes, runtime object search, memory inspection, or eval-style debugging tools.
- Debug-only: runtime code must never leak into release artifacts except as explicit no-op stubs.
- Source-owned, artifact-first where needed: prefer annotations, generated registries, DI overrides, and AGP instrumentation over external hooking. For flows that must remain business-transparent, especially media input, debug builds should use debug-only dependency wiring, bytecode call-site rewrite, runtime bridges, and MCP fixture control instead of requiring app code to import AI debug APIs.

## 3. Baseline

Recommended baseline:

| Area | Decision |
| --- | --- |
| Runtime minimum Android version | API 26+ by default |
| Preferred test fleet | Android 12+ / API 31+ |
| Compatibility fallback | API 23+ can be considered later for non-power features |
| compileSdk | 36 or 36.1 depending on AGP line |
| JDK | 17 |
| Gradle plugin support, near term | AGP 8.12 / 8.13 first |
| Gradle plugin support, follow-up | AGP 9.1 after plugin ecosystem verification |
| Kotlin | Kotlin 2.x; prefer current stable used by target apps |
| Transport to app | ADB local tunnel: `adb forward` for host-to-app calls, `adb reverse` for app-to-daemon callbacks |
| Agent protocol | MCP server exposed by local daemon |

Rationale:

- AndroidX libraries now document a default `minSdk` of 23.
- Room 2.8.x moved Android minSDK to API 23.
- OkHttp 4.x supports Android 5.0+ / API 21+, so it is not the limiting factor.
- ART Tooling Interface / JVMTI is available from Android 8.0, which matches API 26 if later deep instrumentation is needed.
- Test devices are controlled and modern, so optimizing for API 31+ test environments is acceptable.
- AGP 9.1 is available, but it has stricter Gradle and plugin requirements. Supporting AGP 8.12/8.13 first reduces integration risk.

## 4. Architecture

```text
AI Agent / IDE / CLI
        |
        | MCP
        v
ai-debug-daemon
  - MCP server
  - ADB local tunnel / device connection
  - rule and snapshot store
  - artifact/report store
  - optional scenario adapter
        |
        | localhost via ADB local tunnel
        v
ai-debug-runtime AAR in debug app
  - network mock engine
  - media input fixture engine
  - state/action registry
  - override store
  - probe/hook registry
  - trace collector
  - storage adapters
  - audit log
        ^
        |
ai-debug-gradle-plugin
  - injects debug runtime
  - injects no-op release runtime
  - KSP/codegen for annotations
  - AGP ASM instrumentation and call-site rewrite
  - capability schema export
  - release safety checks
```

Suggested packages:

- `ai-debug-runtime`: Android AAR used only by debug/internal builds.
- `ai-debug-runtime-noop`: no-op API for release builds.
- `ai-debug-gradle-plugin`: Gradle plugin for injection, KSP, ASM, schema export, and release checks.
- `ai-debug-daemon`: local MCP server and device/app controller.
- `ai-debug-protocol`: shared JSON schema and Kotlin/TypeScript models.

## 5. Capability Model

The AI should discover capabilities before mutating them.

Example descriptor:

```json
{
  "path": "user.isVip",
  "kind": "state",
  "type": "boolean",
  "mutable": true,
  "description": "Whether the current user has VIP entitlement",
  "tags": ["user", "checkout"],
  "pii": "none",
  "resetPolicy": "restore_snapshot"
}
```

Capability groups:

- `network.*`: inspect, mock, mutate, delay, fail, replay, assert.
- `state.*`: list, get, set, reset, snapshot, restore, diff.
- `storage.*`: SharedPreferences, Room/SQLite, DataStore.
- `override.*`: clock, feature flags, remote config, location, permission, repository next result.
- `probe.*`: search symbols, get/set whitelisted fields, trace methods.
- `hook.*`: override selected method return, throw selected exception, clear override.
- `debug.*`: eval, app-process command execution, object search, memory inspection, and snippet execution.
- `media.*`: discover media input targets, stage audio/camera fixtures, inject frames or PCM streams, record lifecycle/history, and assert fixture consumption.
- `report.*`: export audit logs, network history, state diff, and trace artifacts.

## 6. Network Control

This is the first MVP pillar.

Primary path:

- OkHttp application interceptor for record, mock, response mutation, delay, failure, and assertions.
- Retrofit support through OkHttp plus optional request metadata.
- JSON response mutation with JSONPath / JSONPatch.
- Record-to-mock flow from real traffic.
- Rule scoping by session, scenario, and call count.

MVP example:

```yaml
network.mutateResponse:
  match:
    method: GET
    urlRegex: ".*/api/profile"
  patch:
    - op: replace
      path: "$.data.isVip"
      value: false
  times: 1
```

Expected MCP tools:

```text
network.history
network.get
network.mock
network.mutateResponse
network.clearRules
network.recordToMock
network.assertCalled
```

Later extensions:

- GraphQL `operationName` and variables matching.
- gRPC unary call mocking with `ClientInterceptor`.
- Protobuf descriptor-aware decode and mutation.
- WebSocket inbound message injection.
- Request replay.

## 7. State And Dependency Control

This is the core product difference beyond network mock.

### Manual State Registry

App code can expose typed debug state:

```kotlin
@AiState(path = "user.isVip", mutable = true)
fun isVip(): Boolean = sessionStore.currentUser?.isVip == true

@AiSetter(path = "user.isVip")
fun setVip(value: Boolean) {
    sessionOverride.setVip(value)
}

@AiAction(path = "auth.expireToken")
fun expireToken() {
    tokenStore.expireForDebug()
}
```

Expected MCP tools:

```text
state.list
state.get
state.set
state.reset
state.snapshot
state.restore
state.diff
```

### DI Override

Support common Android app patterns:

- Hilt debug modules.
- Koin debug modules.
- Manual DI wrapper APIs.

Common override targets:

- `FeatureFlags`
- `RemoteConfig`
- `Clock`
- `LocationProvider`
- `PermissionProvider`
- `SessionProvider`
- `ExperimentProvider`
- `PaymentRepository`
- `NetworkStatusProvider`

Example:

```text
override.set("feature.newCheckout", true)
override.set("clock.now", "2026-04-24T10:00:00+08:00")
override.set("payment.nextResult", {"type": "failure", "code": "CARD_DECLINED"})
```

### Storage Adapters

MVP:

- SharedPreferences get/set/snapshot/restore.
- Room/SQLite query/exec/snapshot/restore.
- DataStore Preferences get/set/snapshot/restore.

Later:

- Proto DataStore mutation through app-provided serializers.
- Encrypted storage adapters where the app explicitly provides debug access.

## 8. Compile-Time Probe And Hook

Because apps are built from source, the preferred approach is:

```text
compile-time instrumentation creates safe hook points
runtime MCP calls control hook behavior
```

This should be implemented with AGP instrumentation APIs and ASM, not legacy Transform APIs.

Supported access modes:

1. Annotation-based:

```kotlin
@AiProbe("checkout.quote")
suspend fun quote(...)
```

2. Gradle configuration:

```kotlin
aiDebug {
    probePackage("com.example.app")
    exposeField("com.example.session.UserSession", "currentUser")
    traceMethod("com.example.checkout.CheckoutRepository.quote")
    overrideMethod("com.example.flags.FeatureFlags.isEnabled")
}
```

3. Generated symbol index:

```text
probe.search("vip")
probe.search("checkout")
probe.search("FeatureFlags.isEnabled")
```

Expected MCP tools:

```text
probe.search
probe.describe
probe.getField
probe.setField
trace.start
trace.stop
trace.calls
hook.overrideReturn
hook.overrideThrow
hook.clear
```

Important constraints:

- Default to white-listed packages or annotations.
- Expose object search, memory inspection, and eval as first-class AI debugging tools with schemas, policy metadata, audit logs, and cleanup hooks.
- Do not mutate fields that cannot be restored.
- Prefer typed state/DI override when available.
- Use audit logs for every read/write/override.

## 9. Runtime Search, Eval, And Snippet Runner

Runtime search and eval are core parts of the AI debugging toolset, not a separate mode. The AI should be able to use them when typed state or network mocking is not enough, while each tool still reports what it can read, mutate, restore, and audit.

Preferred progression:

1. JSONPath/JSONPatch for network bodies.
2. SQL for database adapters.
3. Restricted expression DSL for conditions and transforms.
4. Restricted Kotlin/Java debug snippets compiled by the daemon and loaded by the debug runtime.
5. App-process eval / shell-like debug commands exposed as normal MCP tools with explicit schemas and audit.
6. Heap/object graph search for class names, field names, values, and references.
7. Optional JVMTI / Frida / native-agent exploration for API 26+ debug builds.

Snippet design should be capability-scoped:

```kotlin
class ToggleVipSnippet : AiDebugSnippet {
    override fun run(env: AiDebugEnv): JsonElement {
        env.state.set("user.isVip", false)
        return env.state.get("user.isVip")
    }
}
```

Runtime debugging tools should be explicit, discoverable, and auditable rather than hidden behind broad generic commands. They can include app-process eval, heap/object graph search, and low-level memory inspection. The default agent workflow should still prefer typed capabilities when they exist because they are easier to reproduce and explain, but the AI must be able to fall back to lower-level dynamic debugging when exploring unknown code.

## 10. Media Input Control

Media input control is split into three specs to keep implementation tasks clear while preserving the shared business-transparent architecture:

- `012-media-input-foundation`: target discovery, fixture staging, shared protocol, MCP fixture tools, runtime foundation, and release-safety baselines.
- `013-audio-input-control`: AudioRecord read/lifecycle rewriting, WAV/PCM consumption, audio history, and audio assertions.
- `014-camera-input-control`: CameraX analyzer wrapping, ML Kit `InputImage` factory rewriting, configured custom frame hooks, camera history, and frame assertions.

All three specs should use a **business-transparent / bytecode-first** design. They must not require product code to import AI debug APIs, implement AI debug interfaces, or branch on debug state.

The feature is enabled by:

1. Adding the debug-only runtime dependency.
2. Applying Gradle plugin instrumentation.
3. Optionally declaring hook rules in the Gradle DSL.
4. Controlling runtime behavior through MCP or HTTP.

Business code should continue to look like normal Android and SDK usage:

```kotlin
audioRecord.read(buffer, offset, size)
imageAnalysis.setAnalyzer(executor, analyzer)
InputImage.fromMediaImage(mediaImage, rotationDegrees)
detector.process(inputImage)
```

Debug artifacts rewrite selected call sites to `AiDebugMediaHookBridge`. Release artifacts must not be instrumented and must not include the runtime bridge, fixture store, MCP server, or localhost endpoint.

### Media Control Layers

Layer 1: Known Android / SDK boundary instrumentation.

- `AudioRecord.read(...)` overloads.
- `AudioRecord.startRecording()`, `stop()`, and `release()` for lifecycle history and assertions.
- `ImageAnalysis.setAnalyzer(...)` and `ImageAnalysis.clearAnalyzer()`.
- ML Kit `InputImage.fromXxx(...)` factories.
- Selected ML Kit `detector.process(InputImage)` calls where practical.

Layer 2: Target discovery.

- Every rewritten call site injects a stable `callSiteId`.
- Runtime registers observed targets when a bridge is first hit.
- Agents query targets through `media.targets.list` before injecting fixtures.

Layer 3: Runtime bridge injection.

- If a fixture rule matches the target, the bridge replaces input data.
- If no rule matches, the bridge calls the original Android / SDK API.
- Every replacement, fallback, lifecycle event, and consumption event is audited.

Layer 4: Config-driven custom hooks.

- Gradle DSL can declare app-specific CV, OCR, QR, or audio processor method signatures.
- The plugin rewrites those call sites without requiring source changes.

Layer 5: Optional source adapters.

- Source-friendly adapters such as `AudioInputSource` or `CameraFrameSource` can exist for teams that want explicit integration.
- They are not part of the no-intrusion MVP contract.

### Target IDs

`targetId` and `callSiteId` are the core of the no-intrusion model. A stable call-site id should be derived from:

```text
ownerClass + methodName + methodDesc + bytecodeInstructionIndex + apiSignature
```

Examples:

```text
audio:audiorecord:read:com.example.voice.WakeWordEngine#captureLoop()V@insn87
camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142
mlkit:inputimage:fromMediaImage:com.example.ocr.OcrAnalyzer#analyze(Landroidx/camera/core/ImageProxy;)V@insn33
```

The first runtime hit should register a target such as:

```json
{
  "targetId": "camera:camerax:setAnalyzer:com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "kind": "CAMERA_X_ANALYZER",
  "callSiteId": "com.example.camera.ScannerActivity#bindCamera()V@insn142",
  "hitCount": 1,
  "observed": {
    "width": 1280,
    "height": 720,
    "rotationDegrees": 90,
    "format": "YUV_420_888"
  }
}
```

### Audio MVP

Audio is the most reliable no-intrusion MVP because most app-level capture flows eventually call `AudioRecord.read(...)`.

Required call-site rewrites:

```text
AudioRecord.read(byte[], int, int)
AudioRecord.read(byte[], int, int, int)
AudioRecord.read(short[], int, int)
AudioRecord.read(short[], int, int, int)
AudioRecord.read(float[], int, int, int)
AudioRecord.read(ByteBuffer, int)
AudioRecord.read(ByteBuffer, int, int)
```

Debug bytecode should change a source call like:

```kotlin
val n = audioRecord.read(buffer, offset, size)
```

into bridge dispatch similar to:

```kotlin
val n = AiDebugMediaHookBridge.audioRecordRead(
    audioRecord,
    buffer,
    offset,
    size,
    "callSiteId"
)
```

The bridge must preserve `AudioRecord.read` semantics as closely as possible:

- `READ_BLOCKING` attempts to satisfy the requested amount.
- `READ_NON_BLOCKING` returns available data quickly.
- ByteBuffer behavior must match platform expectations, including non-direct buffer handling and frame-size truncation.
- Error injection must model Android errors such as `ERROR_DEAD_OBJECT`, including delayed error return when data has already been transferred.
- History records should include lifecycle, requested bytes/frames, returned bytes/frames, fixture id, underrun/EOF/error, and timing.

Fixture support:

- WAV and raw PCM staging.
- Conversion to the observed `AudioRecord` sample rate, channel count, and encoding when possible.
- Silence, sine wave, noise, finite stream, looped stream, short read, EOF, and injected error modes.

### Camera MVP

CameraX should start with `ImageAnalysis` because its normal API registers one analyzer through `setAnalyzer()` and removes it through `clearAnalyzer()`.

Primary call-site rewrites:

```text
ImageAnalysis.setAnalyzer(Executor, ImageAnalysis.Analyzer)
ImageAnalysis.clearAnalyzer()
```

The bridge should wrap analyzers:

```kotlin
AiDebugMediaHookBridge.cameraXSetAnalyzer(
    imageAnalysis,
    executor,
    analyzer,
    "callSiteId"
)
```

Supported analyzer modes:

- `replace_on_real_frame`: a real frame drives analyzer timing. If a fixture is active, the wrapper passes a debug `ImageProxy` or fixture-backed data; otherwise it delegates to the real analyzer unchanged.
- `drive_analyzer`: runtime actively invokes the analyzer on its executor using fixture frames at configured fps. This supports deterministic CI scenarios but only covers code paths that can operate on synthetic `ImageProxy` data.

CameraX alone is not enough. `ImageProxy.getImage()` is nullable when the proxy is not wrapping an Android `Image`, so code that gates on `imageProxy.image != null` can skip synthetic `ImageProxy` fixtures. The MVP should therefore also hook ML Kit `InputImage` factories.

ML Kit factory call-site rewrites:

```text
InputImage.fromMediaImage(Image, int)
InputImage.fromBitmap(Bitmap, int)
InputImage.fromByteArray(byte[], int, int, int, int)
InputImage.fromByteBuffer(ByteBuffer, int, int, int, int)
InputImage.fromFilePath(Context, Uri)
```

When a fixture is active, the bridge should return an `InputImage` created from fixture-backed NV21, ByteBuffer, ByteArray, or Bitmap data. When no fixture matches, it should call the original ML Kit factory.

Detector-level `process(InputImage)` hooks can be a follow-up safety net for selected ML Kit detectors, but the initial MVP should prioritize `ImageAnalysis` and `InputImage.fromXxx` because they are clearer, more common boundaries.

### Custom Media Hooks

Self-owned CV / OCR / QR / audio processor calls should use Gradle DSL declarations rather than source changes:

```kotlin
aiDebug {
    mediaInputControl {
        enabledForDebugOnly.set(true)

        audio {
            hookAudioRecordReads.set(true)
        }

        camera {
            hookCameraXAnalyzers.set(true)
            hookMlKitInputImageFactories.set(true)

            customFrameHooks {
                method(
                    owner = "com.example.cv.CardRecognizer",
                    name = "detect",
                    desc = "(Landroid/graphics/Bitmap;)Lcom/example/cv/CardResult;"
                ) {
                    frameArgIndex.set(0)
                    frameKind.set("BITMAP")
                }

                method(
                    owner = "com.example.qr.QrEngine",
                    name = "scan",
                    desc = "([BIII)Lcom/example/qr/QrResult;"
                ) {
                    frameArgIndex.set(0)
                    widthArgIndex.set(1)
                    heightArgIndex.set(2)
                    rotationArgIndex.set(3)
                    frameKind.set("NV21")
                }
            }
        }
    }
}
```

The plugin should use AGP `variant.instrumentation.transformClassesWith(...)` and `variant.instrumentation.setAsmFramesComputationMode(...)` for debuggable variants only.

### MCP Tools

Tools should follow the flow: discover target, stage fixture, inject, assert consumption.

```text
media.capabilities
media.targets.list
media.fixture.register
media.fixture.list
media.fixture.delete

media.audio.inject
media.audio.clear
media.audio.history
media.audio.assertConsumed

media.camera.injectFrames
media.camera.clear
media.camera.history
media.camera.snapshot
media.camera.assertConsumed
```

Large fixtures must not be embedded in JSON. The daemon should stage files with `adb push`; runtime receives path and metadata:

```json
{
  "fixtureId": "vip-card-001",
  "devicePath": "/data/local/tmp/ai-debug/fixtures/vip-card-001.nv21",
  "sha256": "...",
  "mimeType": "application/x-nv21",
  "metadata": {
    "width": 1280,
    "height": 720,
    "rotationDegrees": 90
  }
}
```

### Media Spec Breakdown

1. `012-media-input-foundation`: `media.fixture.register`, fixture list/delete, daemon `adb push`, runtime sha256 and metadata validation, target discovery, and shared release-safety baselines.
2. `013-audio-input-control`: AudioRecord read hook for byte, short, float, and ByteBuffer overloads; WAV/PCM stream conversion; blocking, non-blocking, EOF, short read, and error injection.
3. `014-camera-input-control`: CameraX analyzer hook, synthetic `ImageProxy`, ML Kit `InputImage` factory hook, configured custom frame hooks, and camera frame assertions.
4. Cross-spec dogfood: sample app code must not import AI debug APIs; use normal `AudioRecord`, CameraX, and ML Kit-style code; MCP fixture injection drives audio/camera business branches.
5. Cross-spec release verification: no media instrumentation, no debug runtime, no media bridge, no fixture store, no staged fixture logic, and no localhost runtime in release artifacts.

### Media Boundaries

Supported without business source changes:

- AudioRecord PCM read paths.
- CameraX ImageAnalysis analyzer paths.
- ML Kit InputImage construction paths.
- Configured Bitmap, ByteArray, ByteBuffer, and NV21 custom CV method paths.

Not guaranteed in the MVP:

- Preview surface replacement.
- MediaRecorder encoded video/audio stream replacement.
- Camera2 Surface pipeline replacement.
- HAL or fake camera provider replacement.
- Arbitrary native SDK frame ingestion.
- Code that only consumes `android.media.Image` directly and never passes through hookable factories or processors.

## 11. Roadmap

### Phase 0: Protocol And Spike

Goal: prove the daemon-runtime connection and lock down the public API.

Deliverables:

- MCP daemon skeleton.
- In-app debug runtime endpoint.
- ADB local tunnel connection.
- Session token and package allowlist.
- Capability descriptor schema.
- Minimal sample app.

Acceptance:

- An MCP client can connect to a debug app and call `runtime.ping`.
- The app can expose at least one manually registered capability.

### Phase 1: Network-First MVP

Goal: solve the highest-frequency AI self-test problem: changing API response branches.

Deliverables:

- OkHttp interceptor.
- Request/response history.
- Static mock rule.
- JSONPath/JSONPatch response mutation.
- Delay, timeout, HTTP error, disconnect.
- Rule scoping and call count.
- `network.assertCalled`.
- Record-to-mock MVP.

Acceptance:

- AI can run one app build, make `/profile` return `isVip=true`, then mutate it to `false` without rebuilding.
- AI can verify that a request was made and export the captured request/response.

### Phase 2: Typed State MVP

Goal: expose stable business state and reversible mutations.

Deliverables:

- `AiDebug.state`, `AiDebug.action`, and annotation API.
- State descriptor schema.
- `state.list/get/set/reset`.
- Snapshot/restore/diff.
- Audit log.
- SharedPreferences adapter.
- Room/SQLite adapter.
- DataStore Preferences adapter.

Acceptance:

- AI can discover `user.isVip`, set it, run a test step, then restore the previous state.
- AI can query/update a Room table and see state diff in the report.

### Phase 3: Dependency Override

Goal: support branch coverage through app-level providers instead of raw field mutation.

Deliverables:

- Override store.
- Hilt debug module helpers.
- Koin debug module helpers.
- Manual DI wrappers.
- Built-in contracts for clock, feature flags, remote config, location, permission, network status, session, experiment, and repository next-result.

Acceptance:

- AI can force a feature flag, time, permission, or payment outcome through stable MCP calls.
- Overrides are scoped to a session and cleared automatically.

### Phase 4: Gradle Plugin, KSP, And ASM Probe

Goal: reduce manual app integration and enable controlled field/method-level debugging.

Deliverables:

- Gradle plugin.
- Debug runtime auto-injection.
- Release no-op dependency.
- Release artifact scanner.
- KSP-generated state/action registry.
- Capability schema export.
- ASM method trace.
- White-listed field get/set.
- Selected method return/throw override.
- Probe symbol index.

Acceptance:

- AI can search for `vip`, inspect related probes, trace the branch, and override a white-listed method return.
- Release build fails if debug runtime classes or endpoints are included.

### Phase 5: gRPC, GraphQL, And Binary Payloads

Goal: support modern network stacks beyond plain REST/JSON.

Deliverables:

- GraphQL operation matcher.
- GraphQL variables/body mutation.
- gRPC client interceptor.
- Unary gRPC mock.
- Protobuf lite integration.
- App-provided descriptor or serializer registry.

Acceptance:

- AI can mutate a GraphQL or gRPC response field with a stable path and export readable request/response artifacts.

### Phase 6: Runtime Search, Eval, And Deep Instrumentation

Goal: provide dynamic debug tools that AI agents can call directly when typed state, network rules, or compile-time probes are not enough.

Deliverables:

- Restricted expression runner.
- Kotlin/Java snippet compile/load pipeline.
- Capability-scoped snippet environment.
- Eval / app-process command execution as first-class MCP tools.
- Heap/object graph search for selected classes, fields, and values.
- Low-level object and memory inspection where the runtime can support it.
- Optional JVMTI / Frida / native-agent exploration for API 26+ debug builds.

Acceptance:

- AI can run a small typed snippet for reproducible state/network/probe operations.
- AI can evaluate code, search runtime objects, and inspect low-level state through normal tool calls, with audit logs and cleanup hooks.

### Phase 7: Business-Transparent Media Input Control

Goal: let AI agents drive audio and camera branches in debug builds without requiring business source changes.

Deliverables:

- `012-media-input-foundation`, `013-audio-input-control`, and `014-camera-input-control` specs, contracts, plans, tasks, and quickstarts.
- Media fixture staging through daemon-managed `adb push`.
- Runtime media fixture store and sha256/metadata validation.
- `media.targets.list` target discovery from bridge hits.
- AudioRecord read call-site rewrite and lifecycle history.
- CameraX `ImageAnalysis` analyzer wrapping.
- ML Kit `InputImage.fromXxx` factory call-site rewrite.
- Gradle DSL for configured custom frame/audio processor hooks.
- Sample app dogfood with normal `AudioRecord`, CameraX, and ML Kit code.
- Release scanner extensions for media bridge, fixture store, and media instrumentation leakage.

Acceptance:

- A sample app imports no AI debug APIs for audio/camera logic, yet MCP can inject a WAV/PCM fixture into an `AudioRecord.read` loop and assert consumption.
- MCP can inject a PNG/JPEG/NV21 camera fixture through CameraX/ML Kit boundaries and drive a deterministic OCR/QR/image-analysis branch.
- `media.targets.list` exposes observed target ids without source annotations.
- Release artifacts contain no media hook bridge, media fixture store, debug runtime, or rewritten media call sites.

## 12. MVP Scope

Recommended first public MVP:

- Android debug app only.
- API 26+ runtime, Android 12+ recommended test devices.
- MCP daemon.
- `ai-debug-runtime` AAR.
- Manual Gradle plugin integration.
- OkHttp response mock/mutation/history.
- Manual `AiDebug.state/action`.
- Snapshot/restore.
- SharedPreferences and Room/SQLite adapters.
- Minimal runtime object search for selected app packages.
- Minimal `debug.eval` / snippet runner for app-process inspection and controlled mutation.
- Minimal audit/report export.

Tracked as later or deeper capabilities:

- UI automation.
- Full heap and native memory search.
- Business-transparent media input control for AudioRecord, CameraX, ML Kit, and configured custom CV/audio processors.
- Full Frida/JVMTI/native-agent integration.
- iOS/Web/Flutter/React Native adapters.
- Complete gRPC/protobuf support.
- Android Studio plugin.

## 13. Example AI Workflow

```yaml
scenario: vip_branch_checkout
steps:
  - state.snapshot:
      name: before_case

  - network.mutateResponse:
      match:
        method: GET
        urlRegex: ".*/api/profile"
      patch:
        - op: replace
          path: "$.data.isVip"
          value: true

  - network.mutateResponse:
      match:
        method: POST
        urlRegex: ".*/api/checkout/quote"
      patch:
        - op: replace
          path: "$.data.discount"
          value: 20

  - state.set:
      path: feature.newCheckout
      value: true

  - network.assertCalled:
      method: POST
      urlRegex: ".*/api/checkout/quote"

  - state.restore:
      name: before_case
```

## 14. Security And Release Safety

Required controls:

- Runtime only starts when `ApplicationInfo.FLAG_DEBUGGABLE` is true.
- Release builds use no-op artifacts.
- Media input control registers ASM visitors only for debuggable variants.
- Gradle plugin scans release artifacts for forbidden runtime classes, endpoints, and manifest entries.
- Release scans must also reject `AiDebugMediaHookBridge`, media fixture stores, staged fixture logic, and rewritten AudioRecord / CameraX / ML Kit call sites.
- MCP tools are white-listed and schema-validated.
- Eval, app-process command execution, object search, and memory inspection are exposed as first-class MCP tools with explicit schemas, policy metadata, audit logs, and cleanup hooks.
- Session-scoped token for daemon/runtime connection.
- Bind daemon to localhost.
- Use ADB local tunnels rather than exposing device network ports by default.
- Audit every mutation and override.
- Redact sensitive headers, request bodies, state paths, and database columns by policy.
- Snapshot and restore before and after scenario execution.

## 15. References Checked

- AndroidX releases and default `minSdk`: https://developer.android.com/jetpack/androidx/versions
- Room release notes and minSDK changes: https://developer.android.com/jetpack/androidx/releases/room
- DataStore release notes: https://developer.android.com/jetpack/androidx/releases/datastore
- AGP 9.1 compatibility: https://developer.android.com/build/releases/agp-9-1-0-release-notes
- AGP instrumentation API reference: https://developer.android.com/reference/tools/gradle-api/8.8/com/android/build/api/variant/Instrumentation
- AGP instrumentation / Transform API migration background: https://android-developers.googleblog.com/2022/10/prepare-your-android-project-for-agp8-changes.html
- ART Tooling Interface / JVMTI on Android: https://source.android.com/docs/core/runtime/art-ti
- Android AudioRecord API reference: https://developer.android.com/reference/android/media/AudioRecord
- CameraX image analysis guide: https://developer.android.com/media/camera/camerax/analyze
- CameraX ImageProxy API reference: https://developer.android.com/reference/androidx/camera/core/ImageProxy
- ML Kit image input examples: https://developers.google.com/ml-kit/vision/image-labeling/android
- OkHttp interceptors: https://square.github.io/okhttp/features/interceptors/
- OkHttp supported versions: https://square.github.io/okhttp/security/security/
- MockWebServer: https://github.com/square/okhttp/tree/master/mockwebserver
- MCP SDKs: https://modelcontextprotocol.io/docs/sdk
- DTA reference project: https://github.com/yamsergey/dta
- Chucker network inspector reference: https://github.com/ChuckerTeam/chucker
- Android Pluto debug framework reference: https://github.com/androidPluto/pluto
