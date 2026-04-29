# MCP Tool Contract: State And Storage

## `state.list`

```json
{
  "query": "vip",
  "tag": "checkout"
}
```

## `state.get`

```json
{
  "path": "user.isVip"
}
```

Response:

```json
{
  "path": "user.isVip",
  "value": true,
  "mutable": true
}
```

## `state.set`

```json
{
  "path": "user.isVip",
  "value": false
}
```

Response:

```json
{
  "restoreToken": "restore_state_001",
  "path": "user.isVip"
}
```

## `state.snapshot`

```json
{
  "name": "before_checkout",
  "paths": ["user.isVip", "feature.newCheckout"]
}
```

## `state.restore`

```json
{
  "snapshotId": "snapshot_001"
}
```

## `storage.sql.query`

```json
{
  "databaseName": "sample.db",
  "sql": "SELECT id, vip_level FROM users WHERE id = ?",
  "args": ["current"]
}
```

## `storage.sql.exec`

```json
{
  "databaseName": "sample.db",
  "sql": "UPDATE users SET vip_level = ? WHERE id = ?",
  "args": ["expired", "current"]
}
```

## `prefs.set`

```json
{
  "fileName": "sample_flags",
  "key": "newCheckout",
  "value": true,
  "type": "boolean"
}
```

## `datastore.preferences.get`

```json
{
  "name": "sample_settings",
  "key": "newCheckout"
}
```

## `datastore.preferences.set`

```json
{
  "name": "sample_settings",
  "key": "newCheckout",
  "value": true,
  "type": "boolean"
}
```

## `storage.snapshot`

```json
{
  "name": "before_checkout",
  "prefsFiles": ["sample_flags"],
  "databaseNames": ["sample.db"],
  "dataStorePreferenceNames": ["sample_settings"]
}
```

## `override.set`

```json
{
  "key": "feature.newCheckout",
  "value": true,
  "ttlMs": 600000
}
```
