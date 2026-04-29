package com.riftwalker.aidebug.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CleanupRegistry(private val auditLog: AuditLog) {
    private val cleanups = ConcurrentHashMap<String, CleanupEntry>()

    fun register(description: String, cleanup: () -> Unit): String {
        val token = "cleanup_${UUID.randomUUID()}"
        cleanups[token] = CleanupEntry(token, description, cleanup)
        auditLog.recordMutation(
            tool = "session.cleanup.register",
            target = description,
            restoreToken = token,
            status = "success",
        )
        return token
    }

    fun cleanupAll(): Int {
        val snapshot = cleanups.values.toList()
        var successCount = 0
        snapshot.forEach { entry ->
            runCatching {
                entry.cleanup()
                cleanups.remove(entry.token)
                successCount += 1
                auditLog.recordMutation(
                    tool = "session.cleanup.run",
                    target = entry.description,
                    restoreToken = entry.token,
                    status = "success",
                )
            }.onFailure { error ->
                auditLog.recordMutation(
                    tool = "session.cleanup.run",
                    target = entry.description,
                    restoreToken = entry.token,
                    status = "failure",
                    resultSummary = error.message,
                )
            }
        }
        return successCount
    }

    private data class CleanupEntry(
        val token: String,
        val description: String,
        val cleanup: () -> Unit,
    )
}
