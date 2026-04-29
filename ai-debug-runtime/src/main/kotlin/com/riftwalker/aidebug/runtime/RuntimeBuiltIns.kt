package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RuntimeBuiltIns {
    fun register(registry: CapabilityRegistry) {
        registry.register(
            descriptor(
                path = "runtime.ping",
                kind = "runtime",
                mutable = false,
                audit = "read",
                description = "Return runtime identity for the running debug app",
            ),
        )
        registry.register(
            descriptor(
                path = "capabilities.list",
                kind = "runtime",
                mutable = false,
                audit = "read",
                description = "List runtime and app-registered AI debugging capabilities",
            ),
        )
        registry.register(
            descriptor(
                path = "audit.history",
                kind = "runtime",
                mutable = false,
                audit = "read",
                description = "Export audit events for this runtime session",
            ),
        )
        registry.register(
            descriptor(
                path = "session.cleanup",
                kind = "runtime",
                mutable = true,
                audit = "read_write",
                description = "Run all session cleanup hooks and clear session-scoped state",
                cleanup = "self",
                sideEffects = "mutates_runtime_session",
            ),
        )
    }

    private fun descriptor(
        path: String,
        kind: String,
        mutable: Boolean,
        audit: String,
        description: String,
        cleanup: String = "none",
        sideEffects: String = "none",
    ) = CapabilityDescriptor(
        path = path,
        kind = kind,
        schema = buildJsonObject { put("type", "object") },
        mutable = mutable,
        restore = cleanup,
        audit = audit,
        description = description,
        tags = listOf("runtime", "control-plane"),
        policy = PolicyMetadata(
            sideEffects = sideEffects,
            cleanup = cleanup,
            risk = if (mutable) "medium" else "low",
        ),
    )
}
