package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.EvalResponse
import com.riftwalker.aidebug.protocol.JsonSafeValue
import com.riftwalker.aidebug.protocol.NetworkHistoryRequest
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.StateGetRequest
import com.riftwalker.aidebug.protocol.StateSetRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.network.NetworkController
import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.runtime.state.StateController
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EvalEngine(
    private val auditLog: AuditLog,
    private val state: StateController,
    private val network: NetworkController,
    private val probes: ProbeController,
    private val overrides: DebugOverrideStore,
) {
    private val executor = Executors.newCachedThreadPool()

    fun eval(request: EvalRequest): EvalResponse {
        val sideEffects = request.sideEffects
        val event = auditLog.record(
            tool = "debug.eval",
            target = request.language,
            effect = if (sideEffects == "read_only") "read" else "mutate",
            restoreToken = null,
            status = "started",
            argumentsSummary = request.code.take(240),
            resultSummary = null,
        )
        return try {
            val future = executor.submit(Callable { executeDsl(request) })
            val result = future.get(request.timeoutMs.coerceIn(1, 30_000), TimeUnit.MILLISECONDS)
            auditLog.record(
                tool = "debug.eval",
                target = request.language,
                effect = if (sideEffects == "read_only") "read" else "mutate",
                restoreToken = result.cleanupToken,
                status = "success",
                argumentsSummary = request.code.take(240),
                resultSummary = result.value.valuePreview,
            )
            EvalResponse(
                result = result.value.value,
                resultType = result.value.type,
                auditEventId = event.id,
                cleanupToken = result.cleanupToken,
                sideEffects = sideEffects,
            )
        } catch (error: Exception) {
            EvalResponse(
                result = null,
                resultType = "error",
                auditEventId = event.id,
                cleanupToken = null,
                sideEffects = sideEffects,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    private fun executeDsl(request: EvalRequest): EvalResult {
        val code = request.code.trim()
        require(request.language == "debug-dsl" || request.language == "kotlin-snippet") {
            "Unsupported eval language: ${request.language}"
        }
        return when {
            code.startsWith("env.state.get(") -> {
                val path = parseArgs(code, "env.state.get").single().asString()
                val value = state.get(StateGetRequest(path)).value ?: JsonNull
                EvalResult(JsonSafeValueCodec.encode(value))
            }
            code.startsWith("env.state.set(") -> {
                require(request.sideEffects != "read_only") { "state.set requires sideEffects=may_mutate" }
                val args = parseArgs(code, "env.state.set")
                val response = state.set(StateSetRequest(args[0].asString(), args[1].asJson()))
                EvalResult(JsonSafeValueCodec.encode(response.path), response.restoreToken)
            }
            code.startsWith("env.probe.getField(") -> {
                val args = parseArgs(code, "env.probe.getField")
                val value = probes.getField(ProbeGetFieldRequest(args[0].asString(), args[1].asString())).value
                EvalResult(value)
            }
            code.startsWith("env.probe.setField(") -> {
                require(request.sideEffects != "read_only") { "probe.setField requires sideEffects=may_mutate" }
                val args = parseArgs(code, "env.probe.setField")
                val response = probes.setField(ProbeSetFieldRequest(args[0].asString(), args[1].asString(), args[2].asJson()))
                EvalResult(response.value, response.restoreToken)
            }
            code.startsWith("env.override.get(") -> {
                val key = parseArgs(code, "env.override.get").single().asString()
                val value = overrides.get(OverrideGetRequest(key)).value ?: JsonNull
                EvalResult(JsonSafeValueCodec.encode(value))
            }
            code.startsWith("env.override.set(") -> {
                require(request.sideEffects != "read_only") { "override.set requires sideEffects=may_mutate" }
                val args = parseArgs(code, "env.override.set")
                val response = overrides.set(OverrideSetRequest(args[0].asString(), args[1].asJson()))
                EvalResult(JsonSafeValueCodec.encode(response.value ?: JsonNull))
            }
            code.startsWith("env.audit.count()") -> {
                EvalResult(JsonSafeValueCodec.encode(auditLog.history().size))
            }
            code.startsWith("env.network.historyCount()") -> {
                EvalResult(JsonSafeValueCodec.encode(network.history(NetworkHistoryRequest()).records.size))
            }
            else -> EvalResult(evaluateLiteral(code))
        }
    }

    private fun evaluateLiteral(code: String): JsonSafeValue {
        return JsonSafeValueCodec.encode(parseArgument(code).asJson())
    }

    private fun parseArgs(code: String, functionName: String): List<Argument> {
        require(code.startsWith("$functionName(") && code.endsWith(")")) { "Invalid expression: $code" }
        val body = code.removePrefix("$functionName(").removeSuffix(")")
        if (body.isBlank()) return emptyList()
        return splitTopLevel(body).map(::parseArgument)
    }

    private fun splitTopLevel(body: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escape = false
        var bracketDepth = 0
        body.forEach { char ->
            if (escape) {
                current.append(char)
                escape = false
                return@forEach
            }
            when {
                char == '\\' && inString -> {
                    current.append(char)
                    escape = true
                }
                char == '"' -> {
                    current.append(char)
                    inString = !inString
                }
                !inString && (char == '[' || char == '{') -> {
                    bracketDepth++
                    current.append(char)
                }
                !inString && (char == ']' || char == '}') -> {
                    bracketDepth--
                    current.append(char)
                }
                !inString && bracketDepth == 0 && char == ',' -> {
                    parts += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts += current.toString().trim()
        return parts
    }

    private fun parseArgument(raw: String): Argument {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith('"') && trimmed.endsWith('"') -> Argument(JsonPrimitive(trimmed.drop(1).dropLast(1)))
            trimmed == "true" || trimmed == "false" -> Argument(JsonPrimitive(trimmed.toBoolean()))
            trimmed == "null" -> Argument(JsonNull)
            trimmed.toLongOrNull() != null -> Argument(JsonPrimitive(trimmed.toLong()))
            trimmed.toDoubleOrNull() != null -> Argument(JsonPrimitive(trimmed.toDouble()))
            else -> Argument(JsonPrimitive(trimmed))
        }
    }

    private data class Argument(val json: JsonElement) {
        fun asString(): String = json.jsonPrimitive.content
        fun asJson(): JsonElement {
            val primitive = json as? JsonPrimitive ?: return json
            return primitive.booleanOrNull?.let { JsonPrimitive(it) }
                ?: primitive.content.toLongOrNull()?.let { JsonPrimitive(it) }
                ?: primitive.doubleOrNull?.let { JsonPrimitive(it) }
                ?: json
        }
    }

    private data class EvalResult(
        val value: JsonSafeValue,
        val cleanupToken: String? = null,
    )
}
