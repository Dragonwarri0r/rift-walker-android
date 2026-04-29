# Implementation Plan: Camera Input Control

## Scope

Implement no-source-change camera and image fixture injection for CameraX `ImageAnalysis`, ML Kit `InputImage` factories, and configured custom frame processor methods.

## Dependencies

- `012-media-input-foundation` for fixture staging, target registry, media capability registration, common MCP routing, audit, and release-safety extension points.
- `005-gradle-debug-integration` for ASM visitor registration and verification tasks.

## Technical Approach

1. Add camera protocol models:
   - `MediaCameraInjectFramesRequest/Response`,
   - `MediaCameraClearRequest/Response`,
   - `MediaCameraHistoryRequest/Response`,
   - `MediaCameraSnapshotRequest/Response`,
   - `MediaCameraAssertConsumedRequest/Response`,
   - custom frame hook metadata.
2. Add runtime camera controller:
   - rule store,
   - fixture frame decoder/converter,
   - CameraX analyzer wrappers,
   - ML Kit `InputImage` replacement helpers,
   - history and consumption tracking.
3. Add bridge entrypoints for:
   - CameraX `setAnalyzer`,
   - CameraX `clearAnalyzer`,
   - ML Kit `InputImage.fromXxx` factories,
   - configured custom frame hooks.
4. Add Gradle ASM rewrite:
   - detect known CameraX/ML Kit static/virtual calls,
   - inject stable `callSiteId`,
   - route through `AiDebugMediaHookBridge`,
   - keep fallback behavior in bridge code.
5. Add tests:
   - runtime unit tests for fixture selection and fallback,
   - plugin verification for call-site rewrite,
   - sample dogfood for device-level frame consumption.

## Implementation Slices

### Slice A - Protocol And Runtime Rule Store

Add camera request/response models and a `CameraInputController` rule store that works against manually registered `CAMERA_X_ANALYZER`, `MLKIT_INPUT_IMAGE`, and `CUSTOM_FRAME` targets.

### Slice B - Fixture Decode And Frame Model

Implement PNG/JPEG decode and NV21 fixture loading before analyzer integration. Keep metadata explicit: width, height, rotation, timestamp, format, and frame index.

### Slice C - CameraX Analyzer Bridge

Wrap `ImageAnalysis.Analyzer` through a bridge installed by rewritten `setAnalyzer` calls. Start with `replace_on_real_frame`; add `drive_analyzer` only after wrapper and cleanup behavior are stable.

### Slice D - ML Kit Factory Bridge

Rewrite `InputImage.fromXxx` factories and substitute fixture-backed `InputImage` values when a matching rule exists. This slice is required because synthetic `ImageProxy` cannot cover code that requires non-null `imageProxy.image`.

### Slice E - Custom Frame Hooks

Add configured Bitmap / ByteArray / ByteBuffer / NV21 method rewrites after known CameraX and ML Kit hooks are passing. These hooks should reuse the same target discovery and fixture rule model.

### Slice F - Sample Dogfood

Add a normal CameraX + ML Kit-like path in the sample app, stage an image fixture, inject it, assert frame consumption, and prove release safety.

## Ownership

- `ai-debug-protocol`: camera request/response and custom hook metadata models.
- `ai-debug-runtime`: camera controller, frame decode, analyzer wrapper, ML Kit factory helpers, history.
- `ai-debug-daemon`: camera MCP tools and scenario steps.
- `ai-debug-gradle-plugin`: CameraX, ML Kit, and custom frame ASM visitors.
- `sample-app`: normal CameraX / ML Kit-like code path and image fixtures.
- `scripts`: camera smoke and negative release leak checks.

## Gradle DSL Sketch

```kotlin
aiDebug {
    mediaInputControl {
        camera {
            hookCameraXAnalyzers.set(true)
            hookMlKitInputImageFactories.set(true)

            customFrameHooks {
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

## Validation

```bash
./gradlew :ai-debug-protocol:test :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test
./gradlew :sample-app:verifyAiDebugDebugInstrumentation :sample-app:checkAiDebugReleaseSafety
scripts/spec014-camera-smoke.sh
scripts/spec014-negative-release-camera-leak.sh
```

## Risks

- Synthetic `ImageProxy` cannot guarantee non-null `imageProxy.image`; ML Kit factory hooks are required for common analyzer code.
- ML Kit detector `process(InputImage)` hooks are detector-type dependent; start with `InputImage.fromXxx` factories.
- Image conversion should start with PNG/JPEG decode and NV21 fixtures before broader YUV plane simulation.
