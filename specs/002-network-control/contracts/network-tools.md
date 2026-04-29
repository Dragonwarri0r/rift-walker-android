# MCP Tool Contract: Network Control

## `network.history`

Request:

```json
{
  "limit": 50,
  "urlRegex": ".*/profile",
  "includeBodies": false
}
```

Response:

```json
{
  "records": [
    {
      "id": "net_001",
      "method": "GET",
      "url": "https://api.example.com/api/profile",
      "status": 200,
      "durationMs": 120,
      "matchedRuleIds": ["rule_001"],
      "bodyRedacted": false
    }
  ]
}
```

## `network.mutateResponse`

Request:

```json
{
  "match": {
    "method": "GET",
    "urlRegex": ".*/api/profile"
  },
  "patch": [
    {"op": "replace", "path": "$.data.isVip", "value": false}
  ],
  "times": 1,
  "scenarioScope": "vip_branch"
}
```

Response:

```json
{
  "ruleId": "rule_001",
  "restoreToken": "restore_rule_001"
}
```

## `network.mock`

Request:

```json
{
  "match": {"method": "POST", "urlRegex": ".*/checkout/quote"},
  "response": {
    "status": 500,
    "headers": {"content-type": "application/json"},
    "body": {"error": "server_error"},
    "delayMs": 0
  }
}
```

## `network.fail`

Request:

```json
{
  "match": {"urlRegex": ".*/checkout/quote"},
  "failure": {"type": "timeout", "delayMs": 30000},
  "times": 1
}
```

## `network.assertCalled`

Request:

```json
{
  "match": {"method": "POST", "urlRegex": ".*/checkout/quote"},
  "minCount": 1
}
```

Response:

```json
{
  "passed": true,
  "count": 1,
  "recordIds": ["net_002"]
}
```

## `network.recordToMock`

Request:

```json
{
  "recordId": "net_001",
  "times": 1
}
```

Response:

```json
{
  "ruleId": "rule_002",
  "restoreToken": "cleanup_002",
  "sourceRecordId": "net_001",
  "match": {"method": "GET", "urlRegex": "\\Qhttps://api.example.com/api/profile\\E"},
  "status": 200,
  "bodyCaptured": true,
  "bodyRedacted": false
}
```

## `network.clearRules`

Request:

```json
{
  "ruleIds": ["rule_001"]
}
```

Response:

```json
{
  "cleared": 1
}
```
