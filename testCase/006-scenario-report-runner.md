# 006 Scenario Report Runner Agent Test

## Goal

Verify that an agent can compose existing tools into a deterministic scenario,
observe ordered step results, and generate an artifact-rich report.

## Agent Flow

1. Confirm scenario tools exist.

Use the MCP client's tool list/discovery step and verify that daemon tools include
`scenario.run` and `report.generate`. These are daemon orchestration tools, so they
do not have to appear in runtime `capabilities.list`.

Expected evidence:

- `scenario.run` and `report.generate` are visible through the daemon MCP tool registry.

2. Run a passing scenario.

```json
{
  "tool": "scenario.run",
  "arguments": {
    "name": "agent_vip_state_smoke",
    "continueOnError": false,
    "steps": [
      {"id":"snapshot","tool":"state.snapshot","arguments":{"name":"before_agent_vip_state_smoke","paths":["user.isVip"]}},
      {"id":"set_vip","tool":"state.set","arguments":{"path":"user.isVip","value":true}},
      {"id":"read_vip","tool":"state.get","arguments":{"path":"user.isVip"}}
    ]
  }
}
```

Expected evidence:

- Response status is `passed`.
- `steps[0..2]` are in request order.
- Every step has `startedAtEpochMs`, `durationMs`, and either `result` or `error`.
- `read_vip.result.value == true`.

3. Run a failing stop-on-error scenario.

```json
{
  "tool": "scenario.run",
  "arguments": {
    "name": "agent_stop_on_error",
    "continueOnError": false,
    "steps": [
      {"id":"bad_state","tool":"state.get","arguments":{"path":"missing.path"}},
      {"id":"must_skip","tool":"state.get","arguments":{"path":"user.isVip"}}
    ]
  }
}
```

Expected evidence:

- Scenario status is `failed`.
- `bad_state.status == failed`.
- `must_skip.status == skipped`.

4. Run a continue-on-error scenario.

```json
{
  "tool": "scenario.run",
  "arguments": {
    "name": "agent_continue_on_error",
    "continueOnError": true,
    "steps": [
      {"id":"bad_state","tool":"state.get","arguments":{"path":"missing.path"}},
      {"id":"must_continue","tool":"state.get","arguments":{"path":"user.isVip"}}
    ]
  }
}
```

Expected evidence:

- The failed step is preserved.
- The later step runs and has a result.

5. Generate a report.

```json
{
  "tool": "report.generate",
  "arguments": {
    "runId": "<runId from passing scenario>",
    "includeAudit": true,
    "includeNetworkHistory": true,
    "includeBodies": false
  }
}
```

Expected evidence:

- Response includes an absolute report `path`.
- Report JSON includes the run, audit collection, network history collection, and `errors`.
- The artifact is readable on disk.

6. Confirm recursion protection.

```json
{
  "tool": "scenario.run",
  "arguments": {
    "name": "agent_recursive_rejected",
    "steps": [{"id":"nested","tool":"scenario.run","arguments":{}}]
  }
}
```

Expected evidence:

- The step fails with a message that nested `scenario.run` is rejected.

## Failure Triage

- Missing tool: daemon did not register scenario MCP tools.
- Scenario passes but report lacks audit/network fields: report collection path is broken.
- Report writes but run id is absent: stored-run lookup failed.
