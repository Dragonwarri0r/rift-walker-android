# MCP Tool Contract: Dynamic Debugging

## `debug.objectSearch`

Request:

```json
{
  "query": "vip",
  "packages": ["com.example.app"],
  "includeFields": true,
  "limit": 20
}
```

Response:

```json
{
  "results": [
    {
      "handle": "obj_001",
      "className": "com.example.app.session.UserSession",
      "fieldPath": "currentUser.isVip",
      "valuePreview": "true",
      "readable": true,
      "writable": true
    }
  ]
}
```

## `debug.eval`

Request:

```json
{
  "language": "debug-dsl",
  "code": "env.state.get(\"user.isVip\")",
  "timeoutMs": 2000,
  "sideEffects": "read_only|may_mutate"
}
```

Response:

```json
{
  "result": true,
  "resultType": "boolean",
  "auditEventId": "evt_010",
  "cleanupToken": null
}
```

Supported MVP expressions:

```text
env.state.get("path")
env.state.set("path", value)
env.probe.getField("obj_id", "field.path")
env.probe.setField("obj_id", "field.path", value)
env.override.get("key")
env.override.set("key", value)
env.network.historyCount()
env.audit.count()
```

## `probe.getField`

Request:

```json
{
  "target": "obj_001",
  "fieldPath": "currentUser.isVip"
}
```

## `probe.setField`

Request:

```json
{
  "target": "obj_001",
  "fieldPath": "currentUser.isVip",
  "value": false
}
```

Response:

```json
{
  "restoreToken": "restore_field_001",
  "value": false
}
```

## `hook.overrideReturn`

Request:

```json
{
  "methodId": "com.example.flags.FeatureFlags#isEnabled(java.lang.String)",
  "whenArgs": ["new_checkout"],
  "returnValue": true,
  "times": 1
}
```

## `hook.clear`

Request:

```json
{
  "hookId": "hook_001"
}
```

## `hook.throw`

Request:

```json
{
  "methodId": "com.example.flags.FeatureFlags#isEnabled(java.lang.String)",
  "whenArgs": ["new_checkout"],
  "message": "Injected failure",
  "times": 1
}
```
