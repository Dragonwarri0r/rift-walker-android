# 004 Dynamic Debugging Agent Test

## Goal

Guide an agent through the exploratory debug loop: discover an app object, inspect or mutate it,
verify the result independently, then clean up and prove the action was audited.

## Agent Flow

1. Establish runtime identity.

```json
{"tool":"runtime.ping","arguments":{}}
{"tool":"capabilities.list","arguments":{"query":"debug"}}
```

Expected evidence:

- `runtime.ping.debuggable == true`
- capabilities include `debug.objectSearch`, `debug.eval`, `probe.getField`, `probe.setField`,
  `hook.overrideReturn`, `hook.throw`, and `hook.clear`.

2. Search for the sample VIP object.

```json
{
  "tool": "debug.objectSearch",
  "arguments": {
    "query": "vip",
    "packages": ["com.riftwalker.sample"],
    "includeFields": true,
    "limit": 20
  }
}
```

Expected evidence:

- At least one result references `SampleSession` or `sample.session.isVip`.
- The selected result has a stable `handle`, `readable == true`, and ideally `writable == true`.

3. Read and mutate through the safest available path.

If `probe.getField` is available for the result:

```json
{"tool":"probe.getField","arguments":{"target":"<handle>","fieldPath":"isVip"}}
{"tool":"probe.setField","arguments":{"target":"<handle>","fieldPath":"isVip","value":true}}
{"tool":"probe.getField","arguments":{"target":"<handle>","fieldPath":"isVip"}}
```

If no probe target is found, use typed state as a fallback and mark the case as "fallback path":

```json
{"tool":"debug.eval","arguments":{"language":"debug-dsl","code":"env.state.set(\"user.isVip\", true)","timeoutMs":2000,"sideEffects":"may_mutate"}}
{"tool":"debug.eval","arguments":{"language":"debug-dsl","code":"env.state.get(\"user.isVip\")","timeoutMs":2000,"sideEffects":"read_only"}}
```

Expected evidence:

- The mutation response includes a cleanup or restore token when the tool supports it.
- An independent read returns `true`.
- `audit.history` contains the debug/probe/eval mutation.

4. Test hook behavior when a configured method exists.

```json
{
  "tool": "hook.overrideReturn",
  "arguments": {
    "methodId": "com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()",
    "returnValue": true,
    "times": 1
  }
}
```

If the method is not configured in this build, the expected outcome is a structured failure.
Do not count an unconfigured method as a runtime failure; count it as missing fixture coverage.

5. Cleanup.

```json
{"tool":"hook.clear","arguments":{"hookId":"<hookId>"}}
{"tool":"session.cleanup","arguments":{}}
```

## Failure Triage

- Empty object search: confirm the sample app called `AiDebug.trackObject("sample.session", ...)`.
- Probe read fails but state path works: dynamic object tracking is the issue, not typed state.
- Mutation succeeds but audit is missing: audit instrumentation is broken.
- Hook install fails for a configured method: check Gradle instrumentation from case 009.
