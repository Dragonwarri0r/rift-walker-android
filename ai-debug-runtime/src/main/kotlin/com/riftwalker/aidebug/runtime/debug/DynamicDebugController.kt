package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.EvalRequest
import com.riftwalker.aidebug.protocol.HookClearRequest
import com.riftwalker.aidebug.protocol.HookOverrideReturnRequest
import com.riftwalker.aidebug.protocol.HookThrowRequest
import com.riftwalker.aidebug.protocol.ObjectHandle
import com.riftwalker.aidebug.protocol.ObjectSearchRequest
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import com.riftwalker.aidebug.runtime.network.NetworkController
import com.riftwalker.aidebug.runtime.override.DebugOverrideStore
import com.riftwalker.aidebug.runtime.state.StateController
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

class DynamicDebugController(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
    state: StateController,
    network: NetworkController,
    overrides: DebugOverrideStore,
) {
    private val objects = ObjectRegistry()
    private val probes = ProbeController(objects, auditLog, cleanupRegistry)
    private val hooks = HookStore(auditLog, cleanupRegistry)
    private val eval = EvalEngine(auditLog, state, network, probes, overrides)

    init {
        cleanupRegistry.register("clear dynamic debug session state") {
            objects.clear()
            hooks.clearAll()
        }
    }

    fun track(label: String?, value: Any): ObjectHandle {
        val handle = objects.track(label, value)
        auditLog.recordRead("debug.trackObject", label ?: handle.id, "success", resultSummary = handle.id)
        return handle
    }

    fun objectSearch(request: ObjectSearchRequest) = objects.search(request).also {
        auditLog.recordRead("debug.objectSearch", request.query, "success", resultSummary = "${it.results.size} result(s)")
    }

    fun eval(request: EvalRequest) = eval.eval(request)

    fun getField(request: ProbeGetFieldRequest) = probes.getField(request)

    fun setField(request: ProbeSetFieldRequest) = probes.setField(request)

    fun overrideReturn(request: HookOverrideReturnRequest) = hooks.overrideReturn(request)

    fun throwError(request: HookThrowRequest) = hooks.throwError(request)

    fun clearHook(request: HookClearRequest) = hooks.clear(request)

    fun hookBoolean(methodId: String, args: List<Any?> = emptyList(), real: () -> Boolean): Boolean {
        return resolveHookBoolean(methodId, args) ?: real()
    }

    fun hookString(methodId: String, args: List<Any?> = emptyList(), real: () -> String): String {
        return resolveHookString(methodId, args) ?: real()
    }

    fun hookJson(methodId: String, args: List<Any?> = emptyList(), real: () -> JsonElement): JsonElement {
        val resolution = hooks.resolve(methodId, args) ?: return real()
        if (resolution.rule.action == "throw") error(resolution.rule.throwMessage ?: "Injected hook failure")
        return resolution.rule.returnValue ?: error("Hook ${resolution.rule.id} did not provide a return value")
    }

    fun resolveHookBoolean(methodId: String, args: List<Any?> = emptyList()): Boolean? {
        val resolution = hooks.resolve(methodId, args) ?: return null
        if (resolution.rule.action == "throw") error(resolution.rule.throwMessage ?: "Injected hook failure")
        return resolution.rule.returnValue?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: error("Hook ${resolution.rule.id} did not provide a boolean return")
    }

    fun resolveHookString(methodId: String, args: List<Any?> = emptyList()): String? {
        val resolution = hooks.resolve(methodId, args) ?: return null
        if (resolution.rule.action == "throw") error(resolution.rule.throwMessage ?: "Injected hook failure")
        return resolution.rule.returnValue?.jsonPrimitive?.content
            ?: error("Hook ${resolution.rule.id} did not provide a string return")
    }
}
