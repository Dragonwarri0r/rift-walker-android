# Quickstart: Dynamic Debugging

## Track An Object

```kotlin
class SessionDebugTarget {
    var isVip: Boolean = false
}

val sessionDebugTarget = SessionDebugTarget()

AiDebug.trackObject("session.debugTarget", sessionDebugTarget)
```

## Add A Manual Hook Point

```kotlin
class DebuggableFeatureFlags(private val real: FeatureFlags) : FeatureFlags {
    override fun isEnabled(key: String): Boolean {
        return AiDebug.hookBoolean(
            methodId = "com.example.FeatureFlags#isEnabled(java.lang.String)",
            args = listOf(key),
        ) {
            real.isEnabled(key)
        }
    }
}
```

## Search And Mutate

```json
{"name":"debug.objectSearch","arguments":{"query":"vip","includeFields":true,"limit":20}}
```

```json
{"name":"probe.getField","arguments":{"target":"obj_001","fieldPath":"isVip"}}
```

```json
{"name":"probe.setField","arguments":{"target":"obj_001","fieldPath":"isVip","value":true}}
```

## Eval DSL

```json
{"name":"debug.eval","arguments":{"language":"debug-dsl","code":"env.state.get(\"user.isVip\")","sideEffects":"read_only"}}
```

```json
{"name":"debug.eval","arguments":{"language":"debug-dsl","code":"env.probe.setField(\"obj_001\", \"isVip\", true)","sideEffects":"may_mutate"}}
```

## Hook Override

```json
{
  "name": "hook.overrideReturn",
  "arguments": {
    "methodId": "com.example.FeatureFlags#isEnabled(java.lang.String)",
    "whenArgs": ["new_checkout"],
    "returnValue": true,
    "times": 1
  }
}
```
