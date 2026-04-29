package com.riftwalker.aidebug.daemon

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

class DaemonToolRegistry {
    private val tools = linkedMapOf<String, DaemonTool>()

    fun register(
        name: String,
        description: String,
        inputSchema: JsonObject = buildJsonObject { },
        handler: suspend (JsonElement?) -> JsonElement,
    ) {
        tools[name] = DaemonTool(name, description, inputSchema, handler)
    }

    fun names(): List<String> = tools.keys.toList()

    fun definitions(): List<DaemonToolDefinition> {
        return tools.values.map {
            DaemonToolDefinition(
                name = it.name,
                description = it.description,
                inputSchema = it.inputSchema,
            )
        }
    }

    suspend fun invoke(name: String, arguments: JsonElement?): JsonElement {
        val tool = tools[name] ?: error("Unknown daemon tool: $name")
        return tool.handler(arguments)
    }

    private data class DaemonTool(
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
        val handler: suspend (JsonElement?) -> JsonElement,
    )
}
