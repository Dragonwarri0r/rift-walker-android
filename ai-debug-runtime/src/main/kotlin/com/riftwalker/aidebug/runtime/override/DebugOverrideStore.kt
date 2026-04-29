package com.riftwalker.aidebug.runtime.override

import com.riftwalker.aidebug.protocol.OverrideClearRequest
import com.riftwalker.aidebug.protocol.OverrideClearResponse
import com.riftwalker.aidebug.protocol.OverrideEntry
import com.riftwalker.aidebug.protocol.OverrideGetRequest
import com.riftwalker.aidebug.protocol.OverrideSetRequest
import com.riftwalker.aidebug.protocol.OverrideValueResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.concurrent.ConcurrentHashMap

class DebugOverrideStore(
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val overrides = ConcurrentHashMap<String, OverrideEntry>()

    fun set(request: OverrideSetRequest): OverrideValueResponse {
        val now = System.currentTimeMillis()
        val previous = getEntry(request.key)
        val entry = OverrideEntry(
            key = request.key,
            value = request.value,
            expiresAtEpochMs = request.ttlMs?.let { now + it },
            createdAtEpochMs = now,
        )
        overrides[request.key] = entry
        val restoreToken = cleanupRegistry.register("restore override ${request.key}") {
            if (previous == null) {
                overrides.remove(request.key)
            } else {
                overrides[request.key] = previous
            }
        }
        auditLog.recordMutation(
            tool = "override.set",
            target = request.key,
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.value.toString(),
        )
        return OverrideValueResponse(
            key = request.key,
            exists = true,
            value = entry.value,
            expiresAtEpochMs = entry.expiresAtEpochMs,
        )
    }

    fun get(request: OverrideGetRequest): OverrideValueResponse {
        val entry = getEntry(request.key)
        auditLog.recordRead("override.get", request.key, "success")
        return OverrideValueResponse(
            key = request.key,
            exists = entry != null,
            value = entry?.value,
            expiresAtEpochMs = entry?.expiresAtEpochMs,
        )
    }

    fun list(): List<OverrideEntry> {
        purgeExpired()
        auditLog.recordRead("override.list", target = null, status = "success")
        return overrides.values.sortedBy { it.key }
    }

    fun clear(request: OverrideClearRequest): OverrideClearResponse {
        val cleared = if (request.key == null) {
            val count = overrides.size
            overrides.clear()
            count
        } else {
            if (overrides.remove(request.key) == null) 0 else 1
        }
        auditLog.recordMutation("override.clear", request.key, restoreToken = null, status = "success")
        return OverrideClearResponse(cleared)
    }

    fun setJson(key: String, value: JsonElement, ttlMs: Long? = null) {
        set(OverrideSetRequest(key, value, ttlMs))
    }

    fun getJson(key: String): JsonElement? = getEntry(key)?.value

    fun getBoolean(key: String): Boolean? = getJson(key)?.jsonPrimitive?.booleanOrNull

    fun getString(key: String): String? = getJson(key)?.jsonPrimitive?.content

    fun getInt(key: String): Int? = getJson(key)?.jsonPrimitive?.intOrNull

    fun getLong(key: String): Long? = getJson(key)?.jsonPrimitive?.longOrNull

    fun getDouble(key: String): Double? = getJson(key)?.jsonPrimitive?.doubleOrNull

    fun setBoolean(key: String, value: Boolean, ttlMs: Long? = null) = setJson(key, JsonPrimitive(value), ttlMs)

    fun setString(key: String, value: String, ttlMs: Long? = null) = setJson(key, JsonPrimitive(value), ttlMs)

    fun setInt(key: String, value: Int, ttlMs: Long? = null) = setJson(key, JsonPrimitive(value), ttlMs)

    fun setLong(key: String, value: Long, ttlMs: Long? = null) = setJson(key, JsonPrimitive(value), ttlMs)

    fun setDouble(key: String, value: Double, ttlMs: Long? = null) = setJson(key, JsonPrimitive(value), ttlMs)

    fun featureFlag(key: String, real: () -> Boolean): Boolean = getBoolean("feature.$key") ?: real()

    fun clockNowIso(real: () -> String): String = getString("clock.now") ?: real()

    fun remoteConfigString(key: String, real: () -> String): String = getString("remoteConfig.$key") ?: real()

    fun remoteConfigBoolean(key: String, real: () -> Boolean): Boolean = getBoolean("remoteConfig.$key") ?: real()

    fun permission(permissionName: String, real: () -> Boolean): Boolean = getBoolean("permission.$permissionName") ?: real()

    fun networkStatus(real: () -> String): String = getString("network.status") ?: real()

    fun paymentNextResult(real: () -> String): String = getString("payment.nextResult") ?: real()

    private fun getEntry(key: String): OverrideEntry? {
        val entry = overrides[key] ?: return null
        val expiresAt = entry.expiresAtEpochMs
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
            overrides.remove(key, entry)
            return null
        }
        return entry
    }

    private fun purgeExpired() {
        overrides.keys.forEach(::getEntry)
    }
}
