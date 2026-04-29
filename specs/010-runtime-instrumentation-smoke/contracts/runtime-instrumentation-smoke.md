# Contract: Runtime Instrumentation Smoke

## Test Name

`RuntimeEndpointInstrumentedTest`

## Runtime Setup

- Start with `AiDebugRuntime.stop()` to clear any previous singleton endpoint.
- Call `AiDebugRuntime.start(context, port = 0)`.
- Require a positive assigned port.
- Stop the runtime in `finally` or `@After`.

## HTTP Contract

### `GET /runtime/ping`

Headers: none

Expected:

- HTTP 200
- `RuntimePingResponse.sessionId` is non-empty
- `RuntimePingResponse.sessionToken` is non-empty
- `RuntimePingResponse.debuggable` is `true`

### `POST /capabilities/list`

Headers:

- Missing token: none
- Authorized token: `X-Ai-Debug-Token: <sessionToken>`

Body:

```json
{"kind":"all","query":"会员🚀"}
```

Expected:

- Missing token returns HTTP 401.
- Authorized request returns HTTP 200 and a `CapabilityListResponse`.

### `POST /network/clearRules`

Headers:

- `X-Ai-Debug-Token: <sessionToken>`

Body:

```json
{"ruleIds":[]}
```

Expected:

- HTTP 200
- JSON response body is returned.

## Out Of Contract

- ADB reverse setup.
- MCP stdio framing.
- UI interactions.
- Network interceptor behavior.
