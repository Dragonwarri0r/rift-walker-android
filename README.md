# Riftwalker Android

Riftwalker Android 是一个面向 AI coding agent 的 Android debug runtime control plane。它把 debug build 中的网络、状态、存储、依赖覆盖、探针、hook、音频/相机媒体输入和场景报告暴露成稳定的 HTTP/MCP 工具，让 agent 可以覆盖那些只靠点击 UI 或读日志很难触达的运行时分支。

如果你只是想先跑起来，可以直接看下面的 sample app 快速开始；完整进度、模块说明和验证记录放在后面的参考区。

## 快速开始：sample app

构建、安装并启动 sample：

```bash
./gradlew :sample-app:assembleDebug
adb install -r sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb shell am start -n com.riftwalker.sample/.MainActivity
adb forward tcp:37913 tcp:37913
```

获取 runtime session token：

```bash
PING_JSON="$(curl -sS http://127.0.0.1:37913/runtime/ping)"
TOKEN="$(printf '%s' "$PING_JSON" | jq -r .sessionToken)"
printf '%s\n' "$PING_JSON" | jq .
```

除 `/runtime/ping` 之外，runtime HTTP endpoint 都需要 `X-Ai-Debug-Token`：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"query":"sample"}' \
  http://127.0.0.1:37913/capabilities/list | jq .
```

一键跑 dogfood 场景：

```bash
scripts/spec011-dogfood.sh
```

报告会写入：

```text
build/ai-debug/dogfood-reports/*.json
```

一键跑媒体输入验证套件：

```bash
bash scripts/spec015-media-input-verification.sh
```

报告会写入：

```text
build/ai-debug/media-verification/*.json
```

## MCP Daemon

以 stdio MCP server 方式启动本地 daemon：

```bash
./gradlew -q :ai-debug-daemon:run --args="mcp --host-port=37913"
```

daemon 也提供几个直接 CLI 命令：

```bash
./gradlew -q :ai-debug-daemon:run --args="ping --host-port=37913"
./gradlew -q :ai-debug-daemon:run --args="capabilities --host-port=37913"
./gradlew -q :ai-debug-daemon:run --args="audit --host-port=37913"
./gradlew -q :ai-debug-daemon:run --args="network-history --host-port=37913"
./gradlew -q :ai-debug-daemon:run --args="dogfood-sample --host-port=37913"
```

主要 MCP tools：

| 类别 | Tools |
| --- | --- |
| App / ADB | `device.list`、`adb.forward`、`app.forceStop`、`app.launch` |
| Runtime | `runtime.ping`、`runtime.waitForPing`、`capabilities.list`、`audit.history` |
| Network | `network.history`、`network.mock`、`network.mutateResponse`、`network.fail`、`network.clearRules`、`network.assertCalled`、`network.recordToMock` |
| State / action | `state.list`、`state.get`、`state.set`、`state.reset`、`state.snapshot`、`state.restore`、`state.diff`、`action.list`、`action.invoke` |
| Storage | `prefs.*`、`datastore.preferences.*`、`storage.sql.query`、`storage.sql.exec`、`storage.snapshot`、`storage.restore` |
| Override | `override.set`、`override.get`、`override.list`、`override.clear` |
| Dynamic debug | `debug.objectSearch`、`debug.eval`、`probe.getField`、`probe.setField`、`hook.overrideReturn`、`hook.throw`、`hook.clear` |
| Media foundation | `media.capabilities`、`media.targets.list`、`media.fixture.register`、`media.fixture.list`、`media.fixture.delete` |
| Media audio | `media.audio.inject`、`media.audio.clear`、`media.audio.history`、`media.audio.assertConsumed` |
| Media camera | `media.camera.injectFrames`、`media.camera.clear`、`media.camera.history`、`media.camera.snapshot`、`media.camera.assertConsumed` |
| Scenario / report | `scenario.run`、`report.generate` |

## 接入 Android App

在 app 模块应用插件：

```kotlin
plugins {
    id("com.android.application")
    id("com.riftwalker.ai-debug")
    kotlin("android")
}

aiDebug {
    enabledForBuildTypes.set(listOf("debug"))
    probePackage("com.example.app")
    exposeField("com.example.app.Session", "isVip")
    traceMethod("com.example.app.CheckoutViewModel#refresh()")
    overrideMethod("com.example.app.FeatureFlags#isNewCheckoutEnabled()")
    mediaInputControl {
        audio {
            hookAudioRecordReads.set(true)
            hookAudioRecordLifecycle.set(true)
        }
        camera {
            hookCameraXAnalyzers.set(true)
            hookMlKitInputImageFactories.set(true)
        }
    }
}
```

插件默认注入：

- debug build：`:ai-debug-runtime`
- 非 debug build：`:ai-debug-runtime-noop`
- 编译期注解：`:ai-debug-annotations`

在 Application 或首个 Activity 中启动 runtime：

```kotlin
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiDebugRuntime.start(this)
    }
}
```

如果需要网络控制，把 OkHttp interceptor 接入目标 client：

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(AiDebugNetwork.interceptor())
    .build()
```

注册 typed state 和 action：

```kotlin
AiDebug.booleanState(
    path = "user.isVip",
    description = "Whether current user has VIP entitlement",
    tags = listOf("user", "checkout"),
    read = { session.isVip },
    write = { session.isVip = it },
    reset = { session.isVip = false },
)

AiDebug.action(
    path = "checkout.refresh",
    description = "Refresh checkout screen from current debug state",
) {
    refreshCheckout()
    null
}
```

使用 semantic override helper：

```kotlin
fun isNewCheckoutEnabled(): Boolean {
    return AiDebug.overrides().featureFlag("newCheckout") {
        realFeatureFlags.isEnabled("newCheckout")
    }
}
```

追踪对象，供 `debug.objectSearch` 和 `probe.*` 使用：

```kotlin
AiDebug.trackObject("checkout.session", session)
```

注解生成适合简单 `object` 成员：

```kotlin
object DebugCapabilities {
    @AiState(
        path = "sample.annotatedVip",
        type = "boolean",
        description = "Generated sample VIP state",
    )
    var annotatedVip: Boolean = false

    @AiAction(
        path = "sample.generatedAction",
        description = "Generated no-op sample action",
    )
    fun generatedAction() = Unit
}
```

生成产物位于：

```text
sample-app/build/ai-debug/debug-capabilities.json
sample-app/build/ai-debug/probe-symbol-index.json
sample-app/build/generated/source/aiDebug/main/
sample-app/build/ai-debug/instrumentation-debug-report.json
sample-app/build/ai-debug/release-safety-report.json
```

## 常用 HTTP 示例

设置并断言网络 mock：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{
    "match": {"method": "GET", "urlRegex": ".*/api/profile"},
    "response": {
      "status": 200,
      "headers": {"content-type": "application/json"},
      "body": {"data": {"isVip": true, "name": "Mock VIP"}}
    },
    "times": 1
  }' \
  http://127.0.0.1:37913/network/mock | jq .

adb shell am start -n com.riftwalker.sample/.MainActivity --ez fetchProfile true

curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"match":{"method":"GET","urlRegex":".*/api/profile"},"minCount":1,"timeoutMs":3000}' \
  http://127.0.0.1:37913/network/assertCalled | jq .
```

修改 typed state：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"path":"user.isVip","value":true}' \
  http://127.0.0.1:37913/state/set | jq .
```

读写 SharedPreferences：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"fileName":"sample_flags","key":"newCheckout","value":true,"type":"boolean"}' \
  http://127.0.0.1:37913/prefs/set | jq .
```

执行 SQLite 查询：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"databaseName":"sample.db","sql":"SELECT id, vip, name FROM user_profile WHERE id = ?","args":["current"]}' \
  http://127.0.0.1:37913/storage/sql/query | jq .
```

设置 feature override：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"key":"feature.newCheckout","value":true,"ttlMs":5000}' \
  http://127.0.0.1:37913/override/set | jq .
```

搜索对象并读取字段：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"query":"vip","packages":["com.riftwalker.sample"],"includeFields":true}' \
  http://127.0.0.1:37913/debug/objectSearch | jq .
```

最小 `debug.eval`：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"language":"debug-dsl","code":"env.state.get(\"user.isVip\")"}' \
  http://127.0.0.1:37913/debug/eval | jq .
```

覆盖已插桩方法返回值：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{"methodId":"com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()","returnValue":true,"times":5}' \
  http://127.0.0.1:37913/hook/overrideReturn | jq .
```

清理当前 session：

```bash
curl -sS \
  -H "content-type: application/json" \
  -H "X-Ai-Debug-Token: $TOKEN" \
  -d '{}' \
  http://127.0.0.1:37913/session/cleanup | jq .
```

清理后旧 token 会失效，需要重新调用 `/runtime/ping` 获取新 token。

## 网络匹配能力

`NetworkMatcher` 支持：

- HTTP：`method`、`urlRegex`、`headers`、`bodyContains`、`contentTypeContains`
- GraphQL：`graphqlOperationName`、`graphqlQueryRegex`、`graphqlVariables`
- gRPC：`grpcService`、`grpcMethod`
- 场景隔离：`scenarioScope`

响应动作支持：

- `network.mock`：静态响应、headers、body/bodyText、delay
- `network.mutateResponse`：JSON patch，当前用于 buffered JSON body
- `network.fail`：timeout / disconnect
- `network.recordToMock`：从历史记录生成 mock rule

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `ai-debug-protocol` | Kotlin serialization 协议模型，HTTP/MCP 请求响应共享类型 |
| `ai-debug-annotations` | `@AiState`、`@AiAction`、`@AiSetter`、`@AiProbe` 源注解 |
| `ai-debug-runtime` | Android debug AAR：runtime HTTP endpoint、能力注册、网络控制、状态/存储/override、动态调试、媒体输入控制 |
| `ai-debug-runtime-noop` | release/no-op AAR：保持 API 兼容，不启动调试端点，不改变真实行为 |
| `ai-debug-gradle-plugin` | `com.riftwalker.ai-debug` Gradle 插件：依赖注入、代码生成、ASM 插桩、媒体 call-site rewrite、release 泄漏扫描 |
| `ai-debug-daemon` | 本地 stdio MCP server、ADB 工具、runtime HTTP client、scenario/report runner |
| `sample-app` | 端到端样例、dogfood fixture、AudioRecord / ML Kit-like 媒体输入路径 |
| `specs` / `scripts` | 功能规格、契约、quickstart 与验证脚本 |

## 环境要求

- JDK 17
- Android SDK，能使用 `adb`
- Android 设备/模拟器；runtime 模块默认 `minSdk = 26`，推荐 Android 12+ 测试设备
- `curl` 和 `jq`，用于 smoke / deep verification 脚本
- Gradle wrapper 已包含在仓库中

当前构建基线：

| 项 | 版本 |
| --- | --- |
| AGP | `8.13.2` |
| Kotlin | `2.2.0` |
| compileSdk / targetSdk | `36` |
| runtime port | `37913` |

## Release 安全

release build 通过插件注入 `ai-debug-runtime-noop`，不会启动 runtime HTTP endpoint，也不会改变真实逻辑。`checkAiDebugReleaseSafety` 会扫描 release classpath、outputs 和 intermediates，发现 debug runtime、媒体 bridge、fixture store 或被重写的 debug call site 泄漏时失败。

验证：

```bash
./gradlew :sample-app:assembleRelease
scripts/spec05-negative-release-leak.sh
```

## 完成情况

当前工程状态：**内部 alpha / dogfood 可用**。核心 vertical slice 已完成并通过本地与连接设备验证；媒体输入控制已经进入 E2E 验证阶段，但仍保留若干协议测试、边界语义、专用负向脚本和 daemon scenario follow-up。工程还没有作为独立 SDK 发布，外部接入目前更适合通过本仓库的 composite build / project dependency 方式进行。

截至 2026-04-29，`specs/001` 到 `specs/011` 的任务清单均已完成；`specs/012` 到 `specs/015` 已落地媒体输入 foundation、AudioRecord/ML Kit-like 验证路径和 connected-device verification suite，但对应 spec 仍有开放任务。

| 范围 | 状态 | 说明 |
| --- | --- | --- |
| Runtime MCP control plane | 完成 | app 内 localhost runtime、session token、capabilities、audit、cleanup |
| Network control | 完成 | OkHttp record/mock/mutate/fail/assert、redaction、record-to-mock |
| GraphQL / gRPC network matching | 完成 | GraphQL operation/query/variables 匹配，gRPC service/method 匹配 |
| State / action / storage / override | 完成 | typed state、action、SharedPreferences、SQLite/Room、DataStore Preferences、snapshot/restore、TTL override |
| Dynamic debugging | 完成 | object search、debug-dsl eval、field probe、method hook |
| Gradle debug integration | 完成 | debug/runtime 与 release/no-op 依赖注入、annotation registry、ASM trace/hook、schema export、release safety |
| Scenario / report runner | 完成 | MCP scenario.run、report.generate、JSON 报告 |
| Network record-to-mock | 完成 | 从 network history 生成静态 mock rule，带 redaction 与 matcher 派生 |
| Sample app dogfood | 完成 | sample profile branch 场景覆盖 app launch、mock、state、SQL、hook、cleanup、report |
| Media input foundation | 核心可用，仍有开放项 | `16/20`：media capabilities、target discovery、fixture register/list/delete、daemon `adb push` staging、release-safety baseline 已完成；release report 字段、daemon 单测、专用 smoke/negative 脚本待补 |
| Audio input control | E2E 已验证，仍有开放项 | `20/35`：AudioRecord read/lifecycle rewrite、WAV/PCM 解析、runtime/MCP tools、sample normal AudioRecord path 已完成；更细 read 语义、生成音频流、call-site 报告细节、专用脚本待补 |
| Camera input control | E2E 已验证，仍有开放项 | `17/37`：camera MCP tools、ML Kit `InputImage` factory hook、CameraX analyzer target registration、release safety 已完成；PNG/JPEG 解码、synthetic ImageProxy、custom frame hooks、专用脚本待补 |
| Media verification suite | 基本完成并通过 | `69/74`：`scripts/spec015-media-input-verification.sh` 支持 foundation/audio/camera/full-suite 模式，最近完整套件通过；可选负向 audio/camera leak 脚本与 daemon scenario 待补 |

任务清单统计：

| Spec | 进度 |
| --- | --- |
| `001`-`011` | `131/131` complete |
| `012-media-input-foundation` | `16/20` complete |
| `013-audio-input-control` | `20/35` complete |
| `014-camera-input-control` | `17/37` complete |
| `015-media-input-verification-suite` | `69/74` complete |

已验证命令：

```bash
./gradlew test :sample-app:assembleDebug :sample-app:assembleRelease
./gradlew :sample-app:verifyAiDebugDebugInstrumentation
./gradlew :sample-app:checkAiDebugReleaseSafety
./gradlew :ai-debug-runtime:testDebugUnitTest :ai-debug-daemon:test
./gradlew :ai-debug-runtime:connectedDebugAndroidTest
scripts/spec01-smoke.sh
scripts/spec02-smoke.sh
scripts/spec03-smoke.sh
scripts/spec04-smoke.sh
scripts/completed-specs-verify.sh
scripts/spec011-dogfood.sh
scripts/spec05-negative-release-leak.sh
bash scripts/spec015-media-input-verification.sh --foundation
bash scripts/spec015-media-input-verification.sh --audio
bash scripts/spec015-media-input-verification.sh --camera
bash scripts/spec015-media-input-verification.sh
```

最近一次完整 media verification 报告：`build/ai-debug/media-verification/report_media_20260429_183313.json`，状态 `passed`，foundation/audio/camera/buildSafety/cleanup 均通过，验证设备 API 31。

`connectedDebugAndroidTest`、`completed-specs-verify.sh`、`spec01` 到 `spec04` smoke、`spec011-dogfood.sh`、`spec015-media-input-verification.sh` 需要可用的 Android 设备或模拟器。

## 已知边界

- 这不是 UI 自动化框架；UI 点击、截图和 layout tree 应交给 UIAutomator、Maestro、Appium 等工具。
- 当前 runtime endpoint 是 app 内简单 localhost HTTP server，通过 `adb forward` 从宿主访问。
- `debug.eval` 当前是受限 `debug-dsl`，`kotlin-snippet` 只是协议兼容入口，不是完整 Kotlin 编译执行环境。
- annotation 生成器采用轻量源码扫描，复杂场景建议使用手动 `AiDebug.state` / `AiDebug.action` 注册。
- ASM 自动 hook 目前覆盖 boolean 和 String 返回类型；更复杂返回类型可用手动 `AiDebug.hookJson` 或后续扩展。
- 媒体输入控制当前已经验证 sample AudioRecord byte-array read 和 ML Kit-like `InputImage.fromByteArray` 路径；AudioRecord 边界读语义、生成音频流、完整 CameraX synthetic `ImageProxy`、PNG/JPEG 解码和 custom frame hooks 仍在后续任务中。
- 项目尚未提供 Maven 发布脚本、版本化 SDK 包和跨仓库消费示例。
