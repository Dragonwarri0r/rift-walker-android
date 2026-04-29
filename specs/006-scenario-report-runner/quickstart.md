# Quickstart: Scenario Report Runner

## Run A Scenario Through MCP

Call `scenario.run` with ordered tool steps:

```json
{
  "name": "profile_vip_branch",
  "steps": [
    {"id": "snapshot", "tool": "state.snapshot", "arguments": {"name": "before_case"}},
    {"id": "vip", "tool": "state.set", "arguments": {"path": "user.isVip", "value": true}},
    {
      "id": "mock_profile",
      "tool": "network.mock",
      "arguments": {
        "match": {"method": "GET", "urlRegex": ".*/api/profile"},
        "response": {"status": 200, "body": {"name": "Ada", "isVip": true}},
        "times": 1
      }
    },
    {"id": "restore", "tool": "state.restore", "arguments": {"snapshotId": "before_case"}}
  ]
}
```

## Generate A Report

```json
{
  "runId": "run_from_scenario_run",
  "includeAudit": true,
  "includeNetworkHistory": true,
  "includeBodies": false
}
```

Reports are written to:

```text
build/ai-debug/reports/
```

The report is JSON so agents and CI can attach it directly as an artifact.
