# Scenario Tools Contract

## `scenario.run`

Runs an ordered list of daemon tools.

```json
{
  "name": "vip_branch_checkout",
  "continueOnError": false,
  "steps": [
    {
      "id": "snapshot",
      "tool": "state.snapshot",
      "arguments": {"name": "before_case"}
    },
    {
      "id": "vip",
      "tool": "state.set",
      "arguments": {"path": "user.isVip", "value": true}
    },
    {
      "id": "profile",
      "tool": "network.mutateResponse",
      "arguments": {
        "match": {"method": "GET", "urlRegex": ".*/api/profile"},
        "patches": [{"op": "replace", "path": "$.data.isVip", "value": true}],
        "times": 1
      }
    }
  ]
}
```

Response:

```json
{
  "runId": "run_...",
  "name": "vip_branch_checkout",
  "status": "passed",
  "startedAtEpochMs": 1777280000000,
  "finishedAtEpochMs": 1777280000100,
  "durationMs": 100,
  "steps": [
    {
      "index": 0,
      "id": "snapshot",
      "tool": "state.snapshot",
      "status": "passed",
      "startedAtEpochMs": 1777280000000,
      "durationMs": 20,
      "result": {"snapshotId": "snapshot_..."}
    }
  ]
}
```

Step status values are:

```text
passed | failed | skipped
```

## `report.generate`

Writes a report artifact for a stored scenario run.

```json
{
  "runId": "run_...",
  "name": "vip_branch_checkout",
  "includeAudit": true,
  "includeNetworkHistory": true,
  "includeBodies": false
}
```

Response:

```json
{
  "reportId": "report_...",
  "path": "/abs/path/build/ai-debug/reports/report_....json",
  "report": {
    "reportId": "report_...",
    "generatedAtEpochMs": 1777280000200,
    "run": {"runId": "run_...", "status": "passed"},
    "audit": {},
    "networkHistory": {},
    "errors": []
  }
}
```
