package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DynamicDebugBuiltIns {
    fun register(registry: CapabilityRegistry) {
        val readTools = listOf("debug.objectSearch", "debug.eval", "probe.getField")
        val writeTools = listOf("probe.setField", "hook.overrideReturn", "hook.throw", "hook.clear")
        (readTools + writeTools).forEach { path ->
            registry.register(
                CapabilityDescriptor(
                    path = path,
                    kind = when {
                        path.startsWith("probe.") -> "probe"
                        path.startsWith("hook.") -> "hook"
                        else -> "debug"
                    },
                    schema = buildJsonObject { put("type", "object") },
                    mutable = path in writeTools || path == "debug.eval",
                    restore = if (path in writeTools || path == "debug.eval") "cleanup_or_reported" else "none",
                    audit = if (path in writeTools || path == "debug.eval") "read_write" else "read",
                    description = "Built-in dynamic debugging tool: $path",
                    tags = listOf("dynamic-debug", "probe", "hook", "eval"),
                    policy = PolicyMetadata(
                        sideEffects = if (path in writeTools || path == "debug.eval") "may_mutate_app_process" else "none",
                        cleanup = if (path in writeTools || path == "debug.eval") "cleanup_or_reported" else "none",
                        risk = if (path in writeTools || path == "debug.eval") "high" else "medium",
                    ),
                ),
            )
        }
    }
}
