# 011 Dogfood Self-Test Agent Test

## Goal

Exercise the whole sample loop as an AI agent would: build/install, launch, wait for runtime,
run a scenario across app, network, state, storage, override, hook, assert, cleanup, and report.

## Agent Flow

1. Run the dogfood script.

```bash
bash scripts/spec011-dogfood.sh
```

Optional device override:

```bash
DEVICE_SERIAL=<serial> HOST_PORT=37913 DEVICE_PORT=37913 bash scripts/spec011-dogfood.sh
```

Expected evidence:

- The script builds and installs the debug sample app.
- Daemon command `dogfood-sample` returns JSON.
- Scenario name is `dogfood_sample_profile_branch`.
- Scenario status is `passed`.
- The output includes a report path under `build/ai-debug/dogfood-reports`.

2. Inspect the report JSON.

Expected evidence:

- Ordered steps include device/app launch, `runtime.waitForPing`, network mock,
  state/storage/override mutation, hook override, network assertion, cleanup, and report generation.
- `network.assertCalled` passes without a fixed sleep.
- `audit` contains the mutating tools.
- `networkHistory` contains the sample profile request.

3. Verify cleanup after the scenario.

Call:

```json
{"tool":"network.assertCalled","arguments":{"match":{"method":"GET","urlRegex":".*/api/profile"},"minCount":1}}
{"tool":"override.get","arguments":{"key":"feature.newCheckout"}}
{"tool":"state.get","arguments":{"path":"user.isVip"}}
```

Expected evidence:

- Network history may remain as evidence.
- Active network rules, override rules, and hook rules should be cleared.
- Sample state should be restored to its baseline.

## Failure Triage

- `runtime.waitForPing` times out: app launch, port forward, or runtime start failed.
- Network assert times out: profile fetch was not triggered or mock matcher is wrong.
- Report passes but cleanup leaves active rules: scenario cleanup is incomplete.
