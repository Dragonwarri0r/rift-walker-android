# MCP Tool Contract: Network Record To Mock

## `network.recordToMock`

Request with explicit record id:

```json
{
  "recordId": "net_001",
  "times": 1,
  "scenarioScope": "vip_branch"
}
```

Request using a source matcher and target matcher:

```json
{
  "sourceMatch": {
    "method": "GET",
    "urlRegex": ".*/api/profile"
  },
  "targetMatch": {
    "method": "GET",
    "urlRegex": ".*/api/profile"
  },
  "times": 1
}
```

Response:

```json
{
  "ruleId": "rule_001",
  "restoreToken": "cleanup_001",
  "sourceRecordId": "net_001",
  "match": {
    "method": "GET",
    "urlRegex": "\\Qhttps://api.example.com/api/profile\\E"
  },
  "status": 200,
  "bodyCaptured": true,
  "bodyRedacted": false
}
```

Notes:

- The generated rule is a normal `network.mock` rule and can be removed with `network.clearRules` or cleanup.
- Captured bodies use the same redaction/capping policy as `network.history`.
- If `targetMatch` is omitted, the runtime derives a matcher from method, exact URL, GraphQL operation name, or gRPC service/method metadata.
