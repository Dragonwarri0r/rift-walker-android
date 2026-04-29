# 008 Network Record To Mock Agent Test

## Goal

Verify that an agent can capture a successful response, convert it into a static mock,
and replay it safely without leaking raw sensitive data.

## Agent Flow

1. Produce a source record.

If the backend is available, trigger a real request. If not, use a temporary mock to create
a known captured record, then clear it before replay.

```json
{
  "tool": "network.mock",
  "arguments": {
    "match": {"method":"GET","urlRegex":".*/api/profile"},
    "response": {
      "status": 200,
      "headers": {"content-type":"application/json"},
      "body": {"data":{"name":"Ada","accessToken":"secret-token","email":"ada@example.com"}}
    },
    "times": 1
  }
}
```

Launch the sample with `fetchProfile=true`, then call:

```json
{"tool":"network.history","arguments":{"limit":10,"urlRegex":".*/api/profile","includeBodies":true}}
```

Expected evidence:

- A record has `status == 200`.
- `responseBody` exists.
- Sensitive fields are redacted.
- Keep the `recordId`.

2. Convert the record to a mock.

```json
{
  "tool": "network.recordToMock",
  "arguments": {
    "recordId": "<recordId>",
    "times": 2
  }
}
```

Expected evidence:

- Response includes `ruleId`, `restoreToken`, `sourceRecordId`, derived `match`, `status`.
- `bodyCaptured == true`.
- `bodyRedacted == true` if the source contained sensitive fields.

3. Replay through the generated mock.

Clear any old explicit mock rules. Trigger the same sample request again.

Expected evidence from `network.history`:

- Latest record contains the generated `ruleId` in `matchedRuleIds`.
- Status matches the source record status.
- Response body is the captured/redacted body, not raw secret material.

4. Test explicit target matcher.

```json
{
  "tool": "network.recordToMock",
  "arguments": {
    "sourceMatch": {"method":"GET","urlRegex":".*/api/profile"},
    "targetMatch": {"method":"GET","urlRegex":".*/api/profile"},
    "times": 1
  }
}
```

Expected evidence:

- The returned `match` follows `targetMatch`, not only exact captured URL derivation.

5. Cleanup.

```json
{"tool":"network.clearRules","arguments":{"ruleIds":["<generatedRuleId>"]}}
{"tool":"session.cleanup","arguments":{}}
```

## Failure Triage

- No body captured: confirm `network.history(includeBodies=true)` captures safe response previews.
- Raw token/email appears in generated mock: redaction policy is bypassed.
- Generated mock does not match replay: inspect derived matcher and protocol metadata.
