package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.protocol.ObjectHandle
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object AiDebug {
    fun state(
        path: String,
        schema: JsonObject,
        mutable: Boolean,
        nullable: Boolean = true,
        description: String,
        tags: List<String> = emptyList(),
        pii: String = "none",
        resetPolicy: String = "manual",
        read: () -> JsonElement?,
        write: ((JsonElement) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun booleanState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Boolean?,
        write: ((Boolean) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun stringState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> String?,
        write: ((String) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun intState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Int?,
        write: ((Int) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun longState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Long?,
        write: ((Long) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun doubleState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Double?,
        write: ((Double) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = Unit

    fun action(
        path: String,
        description: String,
        inputSchema: JsonObject = buildJsonObject { put("type", "object") },
        tags: List<String> = emptyList(),
        invoke: (JsonElement?) -> JsonElement? = { null },
    ) = Unit

    fun overrides(): DebugOverrideStore = DebugOverrideStore

    fun trackObject(label: String? = null, value: Any): ObjectHandle {
        return ObjectHandle(
            id = "noop",
            label = label,
            className = value.javaClass.name,
            identityHash = System.identityHashCode(value).toString(16),
            registeredAtEpochMs = 0,
        )
    }

    fun hookBoolean(methodId: String, args: List<Any?> = emptyList(), real: () -> Boolean): Boolean = real()

    fun hookString(methodId: String, args: List<Any?> = emptyList(), real: () -> String): String = real()

    fun hookJson(methodId: String, args: List<Any?> = emptyList(), real: () -> JsonElement): JsonElement = real()
}
