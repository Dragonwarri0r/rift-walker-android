# 005 Gradle Debug Integration Agent Test

## Goal

Verify that the Gradle integration wires debug runtime behavior into debug builds,
keeps release builds safe, and exposes generated capabilities to the runtime.

## Agent Flow

1. Build the relevant variants and inspect generated artifacts.

```bash
./gradlew :sample-app:assembleDebug :sample-app:assembleRelease
```

Expected evidence:

- Debug APK builds successfully.
- Release APK builds successfully with the no-op runtime path.
- Generated debug schema and symbol index artifacts exist if `exportSchema` is enabled.

2. Run release safety.

```bash
./gradlew checkAiDebugReleaseSafety
```

If the root task is not registered, search task names:

```bash
./gradlew tasks --all | rg 'AiDebug|aiDebug|ReleaseSafety|releaseSafety'
```

Expected evidence:

- Safety task passes for the normal release artifact.
- The report contains no forbidden runtime classes such as `RuntimeHttpEndpoint` or
  `NetworkControlInterceptor`.

3. Verify generated capabilities at runtime.

Start the sample app and call:

```json
{"tool":"capabilities.list","arguments":{"query":"sample.annotatedVip"}}
{"tool":"state.get","arguments":{"path":"sample.annotatedVip"}}
{"tool":"action.list","arguments":{"query":"sample.generatedAction"}}
```

Expected evidence:

- `sample.annotatedVip` appears as a state capability.
- `sample.generatedAction` appears as an action capability.
- Reads or invocations use the generated registry, not hand-registered fallback code.

4. Run the negative leak check when available.

```bash
bash scripts/spec05-negative-release-leak.sh
```

Expected evidence:

- The script proves an intentional debug-runtime leak causes a targeted release-safety failure.

## Failure Triage

- Debug build fails: inspect plugin application and dependency wiring first.
- Release safety missing: the plugin did not register the expected task for the variant.
- Capability missing but build artifacts exist: generated registry may not be loaded by `AiDebugRuntime.start()`.
- Release APK contains runtime classes: this is a P0 safety failure.
