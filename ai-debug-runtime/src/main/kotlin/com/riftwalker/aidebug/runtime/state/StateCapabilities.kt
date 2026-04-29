package com.riftwalker.aidebug.runtime.state

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.DebugActionDescriptor
import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun DebugStateDescriptor.toCapabilityDescriptor(): CapabilityDescriptor {
    return CapabilityDescriptor(
        path = path,
        kind = "state",
        schema = schema,
        mutable = mutable,
        restore = resetPolicy,
        audit = if (mutable) "read_write" else "read",
        description = description,
        tags = tags + "state",
        policy = PolicyMetadata(
            pii = pii,
            sideEffects = if (mutable) "mutates_app_state" else "none",
            cleanup = resetPolicy,
            risk = if (mutable) "medium" else "low",
        ),
    )
}

internal fun DebugActionDescriptor.toCapabilityDescriptor(): CapabilityDescriptor {
    return CapabilityDescriptor(
        path = path,
        kind = "action",
        schema = inputSchema,
        mutable = true,
        restore = "action_defined",
        audit = "read_write",
        description = description,
        tags = tags + "action",
        policy = PolicyMetadata(
            sideEffects = "runs_app_code",
            cleanup = "action_defined",
            risk = "medium",
        ),
    )
}

object StateBuiltIns {
    fun register(registry: com.riftwalker.aidebug.runtime.CapabilityRegistry) {
        val readTools = listOf("state.list", "state.get", "state.diff", "action.list")
        val writeTools = listOf(
            "state.set",
            "state.reset",
            "state.snapshot",
            "state.restore",
            "action.invoke",
        )
        (readTools + writeTools).forEach { path ->
            registry.register(
                CapabilityDescriptor(
                    path = path,
                    kind = if (path.startsWith("action.")) "action" else "state",
                    schema = buildJsonObject { put("type", "object") },
                    mutable = path in writeTools,
                    restore = if (path in writeTools) "cleanup_or_snapshot" else "none",
                    audit = if (path in writeTools) "read_write" else "read",
                    description = "Built-in $path tool",
                    tags = listOf("state", "semantic-runtime"),
                    policy = PolicyMetadata(
                        sideEffects = if (path in writeTools) "mutates_app_state" else "none",
                        cleanup = if (path in writeTools) "cleanup_or_snapshot" else "none",
                        risk = if (path in writeTools) "medium" else "low",
                    ),
                ),
            )
        }
    }
}
