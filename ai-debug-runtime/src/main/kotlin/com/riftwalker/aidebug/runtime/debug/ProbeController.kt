package com.riftwalker.aidebug.runtime.debug

import com.riftwalker.aidebug.protocol.ProbeFieldResponse
import com.riftwalker.aidebug.protocol.ProbeGetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldRequest
import com.riftwalker.aidebug.protocol.ProbeSetFieldResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry

class ProbeController(
    private val objects: ObjectRegistry,
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    fun getField(request: ProbeGetFieldRequest): ProbeFieldResponse {
        val field = objects.readField(request.target, request.fieldPath)
        auditLog.recordRead(
            tool = "probe.getField",
            target = "${request.target}:${request.fieldPath}",
            status = if (field.readable) "success" else "error",
            resultSummary = field.value.valuePreview ?: field.reason,
        )
        return ProbeFieldResponse(
            target = request.target,
            fieldPath = request.fieldPath,
            value = field.value,
        )
    }

    fun setField(request: ProbeSetFieldRequest): ProbeSetFieldResponse {
        val write = objects.writeField(request.target, request.fieldPath, request.value)
        val restoreToken = cleanupRegistry.register("restore field ${request.target}:${request.fieldPath}") {
            write.restore()
        }
        auditLog.recordMutation(
            tool = "probe.setField",
            target = "${request.target}:${request.fieldPath}",
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.value.toString(),
            resultSummary = write.current.valuePreview,
        )
        return ProbeSetFieldResponse(
            target = request.target,
            fieldPath = request.fieldPath,
            value = write.current,
            restoreToken = restoreToken,
        )
    }
}
