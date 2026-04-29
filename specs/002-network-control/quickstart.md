# Quickstart: Network Control

This validates the first network-control slice:

1. Build and install the sample app.
2. Start the runtime endpoint and create an ADB local tunnel.
3. Install a `/api/profile` mock rule.
4. Trigger the sample app profile request without UI automation.
5. Export `network.history`.

## One Command

```bash
scripts/spec02-smoke.sh
```

Optional environment variables:

```bash
DEVICE_SERIAL=emulator-5554 HOST_PORT=37913 DEVICE_PORT=37913 scripts/spec02-smoke.sh
```

Expected history should include a `GET https://example.com/api/profile` record with a matched rule id and mocked JSON body.
