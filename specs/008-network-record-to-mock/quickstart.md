# Quickstart: Network Record To Mock

1. Launch the debug app and call an endpoint once.

2. Inspect history:

```json
{
  "tool": "network.history",
  "arguments": {
    "limit": 5,
    "includeBodies": true
  }
}
```

3. Convert the captured response into a one-shot mock:

```json
{
  "tool": "network.recordToMock",
  "arguments": {
    "recordId": "net_001",
    "times": 1
  }
}
```

4. Trigger the same app path again. The request is short-circuited by the generated mock.

5. Clean up when the scenario ends:

```json
{
  "tool": "network.clearRules",
  "arguments": {
    "ruleIds": ["rule_001"]
  }
}
```
