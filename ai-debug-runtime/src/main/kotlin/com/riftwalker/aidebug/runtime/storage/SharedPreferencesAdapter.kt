package com.riftwalker.aidebug.runtime.storage

import android.content.Context
import android.content.SharedPreferences
import com.riftwalker.aidebug.protocol.PrefsDeleteRequest
import com.riftwalker.aidebug.protocol.PrefsEntry
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsListRequest
import com.riftwalker.aidebug.protocol.PrefsListResponse
import com.riftwalker.aidebug.protocol.PrefsMutationResponse
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.PrefsValueResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SharedPreferencesAdapter(
    private val context: Context,
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    fun list(request: PrefsListRequest): PrefsListResponse {
        val entries = prefs(request.fileName).all
            .map { (key, value) -> value.toEntry(key) }
            .sortedBy { it.key }
        auditLog.recordRead("prefs.list", request.fileName, "success")
        return PrefsListResponse(request.fileName, entries)
    }

    fun get(request: PrefsGetRequest): PrefsValueResponse {
        val value = prefs(request.fileName).all[request.key]
        auditLog.recordRead("prefs.get", "${request.fileName}:${request.key}", "success")
        return if (value == null) {
            PrefsValueResponse(request.fileName, request.key, exists = false)
        } else {
            val entry = value.toEntry(request.key)
            PrefsValueResponse(request.fileName, request.key, exists = true, type = entry.type, value = entry.value)
        }
    }

    fun set(request: PrefsSetRequest): PrefsMutationResponse {
        val prefs = prefs(request.fileName)
        val previous = prefs.all[request.key]
        val restoreToken = cleanupRegistry.register("restore prefs ${request.fileName}:${request.key}") {
            prefs.edit().apply {
                if (previous == null) {
                    remove(request.key)
                } else {
                    putAny(request.key, previous.toEntry(request.key).type, previous.toEntry(request.key).value)
                }
            }.commit()
        }
        prefs.edit().putAny(request.key, request.type, request.value).commit()
        auditLog.recordMutation(
            tool = "prefs.set",
            target = "${request.fileName}:${request.key}",
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.value.toString(),
        )
        return PrefsMutationResponse(request.fileName, request.key, restoreToken)
    }

    fun delete(request: PrefsDeleteRequest): PrefsMutationResponse {
        val prefs = prefs(request.fileName)
        val previous = prefs.all[request.key]
        val restoreToken = previous?.let { previousValue ->
            cleanupRegistry.register("restore prefs ${request.fileName}:${request.key}") {
                prefs.edit()
                    .putAny(request.key, previousValue.toEntry(request.key).type, previousValue.toEntry(request.key).value)
                    .commit()
            }
        }
        prefs.edit().remove(request.key).commit()
        auditLog.recordMutation("prefs.delete", "${request.fileName}:${request.key}", restoreToken, "success")
        return PrefsMutationResponse(request.fileName, request.key, restoreToken)
    }

    fun dump(fileName: String): Map<String, PrefsEntry> {
        return prefs(fileName).all.mapValues { (key, value) -> value.toEntry(key) }
    }

    fun restore(fileName: String, entries: Map<String, PrefsEntry>) {
        prefs(fileName).edit().clear().apply {
            entries.values.forEach { entry -> putAny(entry.key, entry.type, entry.value) }
        }.commit()
    }

    private fun prefs(fileName: String): SharedPreferences {
        require(fileName.matches(SAFE_NAME)) { "Invalid SharedPreferences file name: $fileName" }
        return context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.Editor.putAny(key: String, typeHint: String?, value: JsonElement?): SharedPreferences.Editor {
        return when (typeHint ?: inferType(value)) {
            "boolean" -> putBoolean(key, value?.jsonPrimitive?.booleanOrNull ?: false)
            "int" -> putInt(key, value?.jsonPrimitive?.intOrNull ?: value?.jsonPrimitive?.longOrNull?.toInt() ?: 0)
            "long" -> putLong(key, value?.jsonPrimitive?.longOrNull ?: 0L)
            "float" -> putFloat(key, value?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f)
            "double" -> putFloat(key, value?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f)
            "stringSet" -> putStringSet(
                key,
                (value as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
            )
            else -> putString(key, value?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() })
        }
    }

    private fun Any?.toEntry(key: String): PrefsEntry {
        return when (this) {
            null -> PrefsEntry(key, "null", JsonNull)
            is Boolean -> PrefsEntry(key, "boolean", JsonPrimitive(this))
            is Int -> PrefsEntry(key, "int", JsonPrimitive(this))
            is Long -> PrefsEntry(key, "long", JsonPrimitive(this))
            is Float -> PrefsEntry(key, "float", JsonPrimitive(this))
            is String -> PrefsEntry(key, "string", JsonPrimitive(this))
            is Set<*> -> PrefsEntry(key, "stringSet", JsonArray(map { JsonPrimitive(it.toString()) }))
            else -> PrefsEntry(key, "string", JsonPrimitive(toString()))
        }
    }

    private fun inferType(value: JsonElement?): String {
        if (value == null || value is JsonNull) return "string"
        if (value is JsonArray) return "stringSet"
        if (value !is JsonPrimitive) return "string"
        val primitive = value.jsonPrimitive
        return when {
            primitive.booleanOrNull != null -> "boolean"
            primitive.intOrNull != null -> "int"
            primitive.longOrNull != null -> "long"
            primitive.doubleOrNull != null -> "double"
            else -> "string"
        }
    }

    private companion object {
        val SAFE_NAME = Regex("[A-Za-z0-9_.-]+")
    }
}
