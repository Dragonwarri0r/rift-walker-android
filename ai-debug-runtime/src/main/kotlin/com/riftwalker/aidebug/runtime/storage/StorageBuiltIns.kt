package com.riftwalker.aidebug.runtime.storage

import com.riftwalker.aidebug.protocol.CapabilityDescriptor
import com.riftwalker.aidebug.protocol.PolicyMetadata
import com.riftwalker.aidebug.runtime.CapabilityRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object StorageBuiltIns {
    fun register(registry: CapabilityRegistry) {
        val readTools = listOf(
            "prefs.list",
            "prefs.get",
            "datastore.preferences.list",
            "datastore.preferences.get",
            "storage.sql.query",
            "storage.snapshot",
        )
        val writeTools = listOf(
            "prefs.set",
            "prefs.delete",
            "datastore.preferences.set",
            "datastore.preferences.delete",
            "storage.sql.exec",
            "storage.restore",
        )
        (readTools + writeTools).forEach { path ->
            registry.register(
                CapabilityDescriptor(
                    path = path,
                    kind = "storage",
                    schema = buildJsonObject { put("type", "object") },
                    mutable = path in writeTools,
                    restore = if (path in writeTools) "cleanup_or_snapshot" else "none",
                    audit = if (path in writeTools) "read_write" else "read",
                    description = "Built-in storage tool: $path",
                    tags = listOf("storage", "prefs", "datastore", "sqlite", "room"),
                    policy = PolicyMetadata(
                        pii = "possible",
                        sideEffects = if (path in writeTools) "mutates_app_storage" else "none",
                        cleanup = if (path in writeTools) "cleanup_or_snapshot" else "none",
                        risk = if (path in writeTools) "medium" else "low",
                    ),
                ),
            )
        }
    }
}
