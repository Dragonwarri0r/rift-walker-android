package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.protocol.DebugActionDescriptor
import com.riftwalker.aidebug.protocol.DebugStateDescriptor
import com.riftwalker.aidebug.protocol.ObjectHandle
import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.runtime.state.ActionRegistration
import com.riftwalker.aidebug.runtime.state.StateRegistration
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
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
    ) {
        AiDebugRuntime.stateController().registerState(
            StateRegistration(
                descriptor = DebugStateDescriptor(
                    path = path,
                    schema = schema,
                    mutable = mutable,
                    nullable = nullable,
                    description = description,
                    tags = tags,
                    pii = pii,
                    resetPolicy = resetPolicy,
                ),
                read = read,
                write = write,
                reset = reset,
            ),
        )
    }

    fun booleanState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Boolean?,
        write: ((Boolean) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = state(
        path = path,
        schema = primitiveSchema("boolean"),
        mutable = write != null || reset != null,
        nullable = true,
        description = description,
        tags = tags,
        read = { read()?.let(::JsonPrimitive) ?: JsonNull },
        write = write?.let { setter -> { value -> setter(value.jsonPrimitive.boolean) } },
        reset = reset,
    )

    fun stringState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> String?,
        write: ((String) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = state(
        path = path,
        schema = primitiveSchema("string"),
        mutable = write != null || reset != null,
        nullable = true,
        description = description,
        tags = tags,
        read = { read()?.let(::JsonPrimitive) ?: JsonNull },
        write = write?.let { setter -> { value -> setter(value.jsonPrimitive.content) } },
        reset = reset,
    )

    fun intState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Int?,
        write: ((Int) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = state(
        path = path,
        schema = primitiveSchema("integer"),
        mutable = write != null || reset != null,
        nullable = true,
        description = description,
        tags = tags,
        read = { read()?.let(::JsonPrimitive) ?: JsonNull },
        write = write?.let { setter -> { value -> setter(value.jsonPrimitive.int) } },
        reset = reset,
    )

    fun longState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Long?,
        write: ((Long) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = state(
        path = path,
        schema = primitiveSchema("integer"),
        mutable = write != null || reset != null,
        nullable = true,
        description = description,
        tags = tags,
        read = { read()?.let(::JsonPrimitive) ?: JsonNull },
        write = write?.let { setter -> { value -> setter(value.jsonPrimitive.long) } },
        reset = reset,
    )

    fun doubleState(
        path: String,
        description: String,
        tags: List<String> = emptyList(),
        read: () -> Double?,
        write: ((Double) -> Unit)? = null,
        reset: (() -> Unit)? = null,
    ) = state(
        path = path,
        schema = primitiveSchema("number"),
        mutable = write != null || reset != null,
        nullable = true,
        description = description,
        tags = tags,
        read = { read()?.let(::JsonPrimitive) ?: JsonNull },
        write = write?.let { setter -> { value -> setter(value.jsonPrimitive.double) } },
        reset = reset,
    )

    fun action(
        path: String,
        description: String,
        inputSchema: JsonObject = buildJsonObject { put("type", "object") },
        tags: List<String> = emptyList(),
        invoke: (JsonElement?) -> JsonElement? = { null },
    ) {
        AiDebugRuntime.stateController().registerAction(
            ActionRegistration(
                descriptor = DebugActionDescriptor(
                    path = path,
                    inputSchema = inputSchema,
                    description = description,
                    tags = tags,
                ),
                invoke = invoke,
            ),
        )
    }

    fun overrides(): DebugOverrideStore = AiDebugRuntime.overrideStore()

    fun trackObject(label: String? = null, value: Any): ObjectHandle {
        return AiDebugRuntime.dynamicDebugController().track(label, value)
    }

    fun hookBoolean(methodId: String, args: List<Any?> = emptyList(), real: () -> Boolean): Boolean {
        return AiDebugRuntime.dynamicDebugController().hookBoolean(methodId, args, real)
    }

    fun hookString(methodId: String, args: List<Any?> = emptyList(), real: () -> String): String {
        return AiDebugRuntime.dynamicDebugController().hookString(methodId, args, real)
    }

    fun hookJson(methodId: String, args: List<Any?> = emptyList(), real: () -> JsonElement): JsonElement {
        return AiDebugRuntime.dynamicDebugController().hookJson(methodId, args, real)
    }

    private fun primitiveSchema(type: String): JsonObject = buildJsonObject {
        put("type", type)
    }
}
