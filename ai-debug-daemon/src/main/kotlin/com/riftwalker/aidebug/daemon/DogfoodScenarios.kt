package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ScenarioRunRequest
import com.riftwalker.aidebug.protocol.ScenarioStep
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object DogfoodScenarios {
    fun sampleProfileBranch(
        serial: String? = null,
        hostPort: Int = 37913,
        devicePort: Int = 37913,
        packageName: String = "com.riftwalker.sample",
        activity: String = "com.riftwalker.sample/.MainActivity",
    ): ScenarioRunRequest {
        return ScenarioRunRequest(
            name = "dogfood_sample_profile_branch",
            continueOnError = true,
            steps = listOf(
                step(
                    id = "forward_runtime",
                    tool = "adb.forward",
                    arguments = buildJsonObject {
                        serial?.let { put("serial", it) }
                        put("hostPort", hostPort)
                        put("devicePort", devicePort)
                        put("removeExisting", true)
                    },
                ),
                step(
                    id = "force_stop_sample",
                    tool = "app.forceStop",
                    arguments = buildJsonObject {
                        serial?.let { put("serial", it) }
                        put("packageName", packageName)
                    },
                ),
                step(
                    id = "launch_sample",
                    tool = "app.launch",
                    arguments = launchArgs(serial, activity, wait = true),
                ),
                step(
                    id = "wait_runtime",
                    tool = "runtime.waitForPing",
                    arguments = buildJsonObject {
                        put("timeoutMs", 5_000)
                        put("pollIntervalMs", 100)
                    },
                ),
                step(
                    id = "list_sample_capabilities",
                    tool = "capabilities.list",
                    arguments = buildJsonObject {
                        put("query", "sample")
                    },
                ),
                step(
                    id = "list_sample_state",
                    tool = "state.list",
                    arguments = buildJsonObject {
                        put("query", "vip")
                        put("tag", "sample")
                    },
                ),
                step(
                    id = "set_vip_state",
                    tool = "state.set",
                    arguments = buildJsonObject {
                        put("path", "user.isVip")
                        put("value", true)
                    },
                ),
                step(
                    id = "set_db_vip",
                    tool = "storage.sql.exec",
                    arguments = buildJsonObject {
                        put("databaseName", "sample.db")
                        put("sql", "UPDATE user_profile SET vip = ? WHERE id = ?")
                        putJsonArray("args") {
                            add("1")
                            add("current")
                        }
                    },
                ),
                step(
                    id = "set_feature_override",
                    tool = "override.set",
                    arguments = buildJsonObject {
                        put("key", "feature.newCheckout")
                        put("value", true)
                    },
                ),
                step(
                    id = "hook_checkout_flag",
                    tool = "hook.overrideReturn",
                    arguments = buildJsonObject {
                        put("methodId", "com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()")
                        put("returnValue", true)
                        put("times", 5)
                    },
                ),
                step(
                    id = "clear_existing_network_rules",
                    tool = "network.clearRules",
                    arguments = buildJsonObject {},
                ),
                step(
                    id = "mock_profile",
                    tool = "network.mock",
                    arguments = buildJsonObject {
                        putJsonObject("match") {
                            put("method", "GET")
                            put("urlRegex", ".*/api/profile")
                        }
                        putJsonObject("response") {
                            put("status", 200)
                            putJsonObject("headers") {
                                put("content-type", "application/json")
                            }
                            putJsonObject("body") {
                                putJsonObject("data") {
                                    put("isVip", true)
                                    put("name", "Dogfood VIP")
                                    put("source", "ai-debug")
                                }
                            }
                        }
                        put("times", 5)
                        put("scenarioScope", "dogfood_sample_profile_branch")
                    },
                ),
                step(
                    id = "trigger_profile_fetch",
                    tool = "app.launch",
                    arguments = launchArgs(serial, activity, wait = true) {
                        put("fetchProfile", true)
                        put("renderLocalState", true)
                    },
                ),
                step(
                    id = "assert_profile_called",
                    tool = "network.assertCalled",
                    arguments = buildJsonObject {
                        putJsonObject("match") {
                            put("method", "GET")
                            put("urlRegex", ".*/api/profile")
                        }
                        put("minCount", 1)
                        put("timeoutMs", 3_000)
                        put("pollIntervalMs", 100)
                    },
                ),
                step(
                    id = "read_profile_history",
                    tool = "network.history",
                    arguments = buildJsonObject {
                        put("urlRegex", ".*/api/profile")
                        put("includeBodies", false)
                    },
                ),
                step(
                    id = "refresh_local_state",
                    tool = "action.invoke",
                    arguments = buildJsonObject {
                        put("path", "sample.refreshLocalState")
                    },
                ),
                step(
                    id = "cleanup_hook",
                    tool = "hook.clear",
                    arguments = buildJsonObject {
                        put("methodId", "com.riftwalker.sample.MainActivity#isNewCheckoutEnabled()")
                    },
                ),
                step(
                    id = "cleanup_override",
                    tool = "override.clear",
                    arguments = buildJsonObject {
                        put("key", "feature.newCheckout")
                    },
                ),
                step(
                    id = "cleanup_network",
                    tool = "network.clearRules",
                    arguments = buildJsonObject {},
                ),
                step(
                    id = "cleanup_state",
                    tool = "state.reset",
                    arguments = buildJsonObject {
                        put("path", "user.isVip")
                    },
                ),
                step(
                    id = "cleanup_storage",
                    tool = "storage.sql.exec",
                    arguments = buildJsonObject {
                        put("databaseName", "sample.db")
                        put("sql", "UPDATE user_profile SET vip = ? WHERE id = ?")
                        putJsonArray("args") {
                            add("0")
                            add("current")
                        }
                    },
                ),
            ),
        )
    }

    private fun step(id: String, tool: String, arguments: JsonElement): ScenarioStep {
        return ScenarioStep(id = id, tool = tool, arguments = arguments)
    }

    private fun launchArgs(
        serial: String?,
        activity: String,
        wait: Boolean,
        extras: (JsonObjectBuilder.() -> Unit)? = null,
    ): JsonElement {
        return buildJsonObject {
            serial?.let { put("serial", it) }
            put("activity", activity)
            put("wait", wait)
            if (extras != null) {
                putJsonObject("extras") {
                    extras()
                }
            }
        }
    }
}
