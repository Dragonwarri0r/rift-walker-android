package com.riftwalker.aidebug.runtime

import com.riftwalker.aidebug.protocol.DebugSession
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class DebugSessionManager(private val cleanupRegistry: CleanupRegistry) {
    @Volatile
    private var current: DebugSession? = null

    fun currentOrCreate(packageName: String): DebugSession {
        val existing = current
        if (existing != null) return existing

        return synchronized(this) {
            current ?: DebugSession(
                sessionId = "session_${UUID.randomUUID()}",
                packageName = packageName,
                token = newToken(),
                startedAtEpochMs = System.currentTimeMillis(),
            ).also { current = it }
        }
    }

    fun validateToken(token: String?): Boolean {
        val session = current ?: return false
        return token == session.token
    }

    fun cleanupCurrent(): Int {
        val count = cleanupRegistry.cleanupAll()
        current = null
        return count
    }

    private fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
