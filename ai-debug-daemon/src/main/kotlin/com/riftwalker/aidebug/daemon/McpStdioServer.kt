package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.ProtocolJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class McpStdioServer(private val tools: DaemonToolRegistry) {
    fun run() {
        val input = generateSequence(::readLine)
        input.forEach { line ->
            if (line.isBlank()) return@forEach
            val response = handleLine(line)
            if (response != null) {
                println(ProtocolJson.json.encodeToString(response))
                System.out.flush()
            }
        }
    }

    private fun handleLine(line: String): JsonRpcResponse? {
        var request: JsonRpcRequest? = null
        return runCatching {
            val decoded = ProtocolJson.json.decodeFromString<JsonRpcRequest>(line)
            request = decoded
            runBlocking {
                dispatch(decoded)
            }
        }.getOrElse { error ->
            JsonRpcResponse(
                id = request?.id,
                error = JsonRpcError(code = -32603, message = error.message ?: "Internal error"),
            )
        }
    }

    private suspend fun dispatch(request: JsonRpcRequest): JsonRpcResponse? {
        if (request.id == null) return null

        val result = when (request.method) {
            "initialize" -> initializeResult()
            "tools/list" -> toolsListResult()
            "tools/call" -> toolsCallResult(request.params)
            else -> return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(code = -32601, message = "Unknown method: ${request.method}"),
            )
        }
        return JsonRpcResponse(id = request.id, result = result)
    }

    private fun initializeResult(): JsonElement {
        return buildJsonObject {
            put("protocolVersion", "2025-11-25")
            put(
                "serverInfo",
                buildJsonObject {
                    put("name", "riftwalker-ai-debug-daemon")
                    put("version", "0.1.0")
                },
            )
            put(
                "capabilities",
                buildJsonObject {
                    put("tools", buildJsonObject { })
                },
            )
        }
    }

    private fun toolsListResult(): JsonElement {
        return buildJsonObject {
            put(
                "tools",
                buildJsonArray {
                    tools.definitions().forEach { definition ->
                        add(ProtocolJson.json.encodeToJsonElement(definition))
                    }
                },
            )
        }
    }

    private suspend fun toolsCallResult(params: JsonElement?): JsonElement {
        val json = params?.jsonObject ?: error("tools/call requires params")
        val name = json["name"]?.jsonPrimitive?.content ?: error("tools/call requires name")
        val arguments = json["arguments"]
        val payload = tools.invoke(name, arguments)
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", ProtocolJson.json.encodeToString(payload))
                        },
                    )
                },
            )
            put("isError", false)
        }
    }
}
