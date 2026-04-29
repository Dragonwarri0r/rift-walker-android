package com.riftwalker.aidebug.runtime.override

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object OverrideBuiltIns {
    fun register(registry: CapabilityRegistry) {
        listOf("override.set", "override.get", "override.list", "override.clear").forEach { path ->
            val mutable = path == "override.set" || path == "override.clear"
            registry.register(
                CapabilityDescriptor(
                    path = path,
                    kind = "override",
                    schema = buildJsonObject { put("type", "object") },
                    mutable = mutable,
                    restore = if (mutable) "cleanup" else "none",
                    audit = if (mutable) "read_write" else "read",
                    description = "Built-in dependency override tool: $path",
                    tags = listOf("override", "dependency", "semantic-runtime"),
                    policy = PolicyMetadata(
                        sideEffects = if (mutable) "mutates_dependency_override_store" else "none",
                        cleanup = if (mutable) "cleanup" else "none",
                        risk = if (mutable) "medium" else "low",
                    ),
                ),
            )
        }
    }
}
