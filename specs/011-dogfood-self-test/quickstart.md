# Quickstart: Dogfood Self-Test Flow

Run the whole sample dogfood flow on a connected device:

```bash
scripts/spec011-dogfood.sh
```

Optional environment variables:

```bash
DEVICE_SERIAL=emulator-5554 HOST_PORT=37913 DEVICE_PORT=37913 scripts/spec011-dogfood.sh
```

The script builds and installs the sample app, then asks the daemon to:

1. forward the runtime port,
2. launch the sample app,
3. wait for `runtime.ping`,
4. install a profile mock,
5. mutate sample state/storage/override/hook state,
6. trigger the profile request,
7. assert the request was captured,
8. clear installed rules and overrides,
9. generate a JSON report under `build/ai-debug/dogfood-reports`.
