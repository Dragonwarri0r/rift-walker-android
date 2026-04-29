package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.protocol.AuditEvent
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class AuditLog {
    private val events = CopyOnWriteArrayList<AuditEvent>()

    fun recordRead(
        tool: String,
        target: String?,
        status: String,
        argumentsSummary: String? = null,
        resultSummary: String? = null,
    ): AuditEvent = record(
        tool = tool,
        target = target,
        effect = "read",
        restoreToken = null,
        status = status,
        argumentsSummary = argumentsSummary,
        resultSummary = resultSummary,
    )

    fun recordMutation(
        tool: String,
        target: String?,
        restoreToken: String?,
        status: String,
        argumentsSummary: String? = null,
        resultSummary: String? = null,
    ): AuditEvent = record(
        tool = tool,
        target = target,
        effect = "mutate",
        restoreToken = restoreToken,
        status = status,
        argumentsSummary = argumentsSummary,
        resultSummary = resultSummary,
    )

    fun record(
        tool: String,
        target: String?,
        effect: String,
        restoreToken: String?,
        status: String,
        argumentsSummary: String?,
        resultSummary: String?,
    ): AuditEvent {
        val event = AuditEvent(
            id = "evt_${UUID.randomUUID()}",
            tool = tool,
            target = target,
            effect = effect,
            restoreToken = restoreToken,
            status = status,
            timestampEpochMs = System.currentTimeMillis(),
            argumentsSummary = argumentsSummary,
            resultSummary = resultSummary,
        )
        events += event
        return event
    }

    fun history(sinceEpochMs: Long? = null): List<AuditEvent> {
        return events
            .filter { sinceEpochMs == null || it.timestampEpochMs >= sinceEpochMs }
            .sortedBy { it.timestampEpochMs }
    }
}
