# MCP Tool Contract: Runtime Control Plane

## `runtime.ping`

Request:

```json
{}
```

Response:

```json
{
  "packageName": "com.example.app",
  "processId": 12345,
  "debuggable": true,
  "apiLevel": 35,
  "runtimeVersion": "0.1.0",
  "sessionId": "session_abc"
}
```

## `capabilities.list`

Request:

```json
{
  "kind": "state|network|debug|probe|hook|storage|override|all",
  "query": "vip"
}
```

Response:

```json
{
  "capabilities": [
    {
      "path": "user.isVip",
      "kind": "state",
      "schema": {"type": "boolean"},
      "mutable": true,
      "restore": "snapshot",
      "audit": "read_write",
      "description": "Whether the current user has VIP entitlement"
    }
  ]
}
```

## `audit.history`

Request:

```json
{
  "sessionId": "session_abc",
  "since": "2026-04-24T10:00:00Z"
}
```

Response:

```json
{
  "events": [
    {
      "id": "evt_001",
      "tool": "state.set",
      "target": "user.isVip",
      "effect": "mutate",
      "restoreToken": "restore_001",
      "status": "success",
      "timestamp": "2026-04-24T10:01:00Z"
    }
  ]
}
```

## Error Shape

```json
{
  "error": {
    "code": "APP_NOT_DEBUGGABLE",
    "message": "Mutable runtime tools are disabled for this app build",
    "recoverable": false
  }
}
```
