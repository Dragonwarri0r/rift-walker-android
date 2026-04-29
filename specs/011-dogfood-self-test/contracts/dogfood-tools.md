# Contract: Dogfood Tools

## `device.list`

Input:

```json
{}
```

Output:

```json
{"devices":[{"serial":"emulator-5554","state":"device"}]}
```

## `adb.forward`

Input:

```json
{"serial":"emulator-5554","hostPort":37913,"devicePort":37913,"removeExisting":true}
```

Output:

```json
{"serial":"emulator-5554","hostPort":37913,"devicePort":37913}
```

## `app.forceStop`

Input:

```json
{"serial":"emulator-5554","packageName":"com.riftwalker.sample"}
```

Output:

```json
{"serial":"emulator-5554","packageName":"com.riftwalker.sample","output":""}
```

## `app.launch`

Input:

```json
{
  "serial": "emulator-5554",
  "activity": "com.riftwalker.sample/.MainActivity",
  "wait": true,
  "extras": {
    "fetchProfile": true,
    "renderLocalState": true
  }
}
```

Output:

```json
{"serial":"emulator-5554","activity":"com.riftwalker.sample/.MainActivity","output":"Starting: Intent ..."}
```

## `runtime.waitForPing`

Input:

```json
{"timeoutMs":5000,"pollIntervalMs":100}
```

Output: `RuntimePingResponse`

## `network.assertCalled`

Additional optional input fields:

```json
{"timeoutMs":3000,"pollIntervalMs":100}
```

The response shape remains `NetworkAssertCalledResponse`.
