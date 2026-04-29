package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookClearResponse
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookRule
import com.riftwalker.aidebug.protocol.HookRuleResponse
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HookStore(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val hooks = ConcurrentHashMap<String, HookRule>()

    fun overrideReturn(request: HookOverrideReturnRequest): HookRuleResponse {
        val rule = HookRule(
            id = "hook_${UUID.randomUUID()}",
            methodId = request.methodId,
            whenArgs = request.whenArgs,
            action = "return",
            returnValue = request.returnValue,
            times = request.times,
            remaining = request.times,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        return install(rule, "hook.overrideReturn")
    }

    fun throwError(request: HookThrowRequest): HookRuleResponse {
        val rule = HookRule(
            id = "hook_${UUID.randomUUID()}",
            methodId = request.methodId,
            whenArgs = request.whenArgs,
            action = "throw",
            throwMessage = request.message,
            times = request.times,
            remaining = request.times,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        return install(rule, "hook.throw")
    }

    fun clear(request: HookClearRequest): HookClearResponse {
        val toClear = hooks.values
            .filter { rule ->
                (request.hookId == null || rule.id == request.hookId) &&
                    (request.methodId == null || rule.methodId == request.methodId)
            }
            .map { it.id }
        toClear.forEach { hooks.remove(it) }
        auditLog.recordMutation("hook.clear", request.hookId ?: request.methodId, restoreToken = null, status = "success")
        return HookClearResponse(toClear.size)
    }

    fun resolve(methodId: String, args: List<Any?> = emptyList()): HookResolution? {
        val jsonArgs = args.map(::toJsonArg)
        val rule = hooks.values
            .filter { it.methodId == methodId }
            .sortedBy { it.createdAtEpochMs }
            .firstOrNull { matches(it.whenArgs, jsonArgs) }
            ?: return null
        val nextRemaining = rule.remaining?.minus(1)
        if (nextRemaining != null && nextRemaining <= 0) {
            hooks.remove(rule.id)
        } else if (nextRemaining != null) {
            hooks[rule.id] = rule.copy(remaining = nextRemaining)
        }
        auditLog.recordRead("hook.resolve", methodId, "success", resultSummary = rule.id)
        return HookResolution(rule)
    }

    fun clearAll() {
        hooks.clear()
    }

    private fun install(rule: HookRule, tool: String): HookRuleResponse {
        hooks[rule.id] = rule
        val restoreToken = cleanupRegistry.register("clear hook ${rule.id}") {
            hooks.remove(rule.id)
        }
        auditLog.recordMutation(tool, rule.methodId, restoreToken, "success", resultSummary = rule.id)
        return HookRuleResponse(rule.id, restoreToken)
    }

    private fun matches(expected: List<JsonElement>, actual: List<JsonElement>): Boolean {
        return expected.isEmpty() || expected == actual
    }

    private fun toJsonArg(arg: Any?): JsonElement {
        return when (arg) {
            null -> kotlinx.serialization.json.JsonNull
            is Boolean -> JsonPrimitive(arg)
            is Number -> JsonPrimitive(arg.toString())
            is String -> JsonPrimitive(arg)
            is JsonElement -> arg
            else -> JsonPrimitive(arg.toString())
        }
    }

    data class HookResolution(val rule: HookRule)
}
