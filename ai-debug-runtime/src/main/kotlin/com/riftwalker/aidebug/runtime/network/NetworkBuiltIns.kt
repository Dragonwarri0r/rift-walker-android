package com.riftwalker.aidebug.runtime.network

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object NetworkBuiltIns {
    fun register(registry: CapabilityRegistry) {
        listOf(
            "network.history" to "Return captured OkHttp request/response records",
            "network.mutateResponse" to "Install a JSON response mutation rule",
            "network.mock" to "Install a static mock response rule",
            "network.fail" to "Install a timeout or disconnect failure rule",
            "network.clearRules" to "Clear one or more network rules",
            "network.assertCalled" to "Assert matching network requests were captured",
            "network.recordToMock" to "Convert a captured network record into a static mock rule",
        ).forEach { (path, description) ->
            val readOnly = path == "network.history" || path == "network.assertCalled"
            registry.register(
                CapabilityDescriptor(
                    path = path,
                    kind = "network",
                    schema = buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", true)
                    },
                    mutable = !readOnly,
                    restore = if (readOnly) "none" else "cleanup",
                    audit = if (readOnly) "read" else "read_write",
                    description = description,
                    tags = listOf("network", "okhttp"),
                    policy = PolicyMetadata(
                        sideEffects = if (readOnly) "none" else "mutates_network_rules",
                        cleanup = if (readOnly) "none" else "remove_rule",
                        risk = if (path == "network.fail") "medium" else "low",
                    ),
                ),
            )
        }
    }
}
