# Quickstart: State, Storage, And Override

## One Command

```bash
scripts/spec03-smoke.sh
```

Optional environment variables:

```bash
DEVICE_SERIAL=emulator-5554 HOST_PORT=37913 DEVICE_PORT=37913 scripts/spec03-smoke.sh
```

## Register typed state

```kotlin
AiDebug.booleanState(
    path = "user.isVip",
    description = "Whether the current user has VIP entitlement",
    tags = listOf("user", "checkout"),
    read = { session.user?.isVip },
    write = { value -> session.overrideVip(value) },
    reset = { session.clearVipOverride() },
)
```

## Register an action

```kotlin
AiDebug.action(
    path = "auth.expireToken",
    description = "Force the current access token to expire",
) {
    tokenStore.expireForDebug()
    null
}
```

## Use dependency overrides

```kotlin
class DebuggableFeatureFlags(private val real: FeatureFlags) : FeatureFlags {
    override fun isEnabled(key: String): Boolean {
        return AiDebug.overrides().featureFlag(key) {
            real.isEnabled(key)
        }
    }
}
```

## Hilt wrapper example

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DebugFeatureFlagModule {
    @Provides
    @Singleton
    fun provideFeatureFlags(real: RealFeatureFlags): FeatureFlags {
        return DebuggableFeatureFlags(real)
    }
}
```

## Koin wrapper example

```kotlin
val debugModule = module {
    single<FeatureFlags> {
        DebuggableFeatureFlags(real = get<RealFeatureFlags>())
    }
}
```

## MCP examples

```json
{"name":"state.set","arguments":{"path":"user.isVip","value":true}}
```

```json
{"name":"prefs.set","arguments":{"fileName":"sample_flags","key":"newCheckout","value":true,"type":"boolean"}}
```

```json
{"name":"storage.sql.exec","arguments":{"databaseName":"sample.db","sql":"UPDATE user_profile SET vip = ? WHERE id = ?","args":["1","current"]}}
```
