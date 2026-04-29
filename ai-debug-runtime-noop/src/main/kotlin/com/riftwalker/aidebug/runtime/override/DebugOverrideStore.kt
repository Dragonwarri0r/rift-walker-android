package com.riftwalker.aidebug.runtime.override

import kotlinx.serialization.json.JsonElement

object DebugOverrideStore {
    fun getJson(key: String): JsonElement? = null
    fun getBoolean(key: String): Boolean? = null
    fun getString(key: String): String? = null
    fun getInt(key: String): Int? = null
    fun getLong(key: String): Long? = null
    fun getDouble(key: String): Double? = null
    fun setJson(key: String, value: JsonElement, ttlMs: Long? = null) = Unit
    fun setBoolean(key: String, value: Boolean, ttlMs: Long? = null) = Unit
    fun setString(key: String, value: String, ttlMs: Long? = null) = Unit
    fun setInt(key: String, value: Int, ttlMs: Long? = null) = Unit
    fun setLong(key: String, value: Long, ttlMs: Long? = null) = Unit
    fun setDouble(key: String, value: Double, ttlMs: Long? = null) = Unit
    fun featureFlag(key: String, real: () -> Boolean): Boolean = real()
    fun clockNowIso(real: () -> String): String = real()
    fun remoteConfigString(key: String, real: () -> String): String = real()
    fun remoteConfigBoolean(key: String, real: () -> Boolean): Boolean = real()
    fun permission(permissionName: String, real: () -> Boolean): Boolean = real()
    fun networkStatus(real: () -> String): String = real()
    fun paymentNextResult(real: () -> String): String = real()
}
