package com.riftwalker.aidebug.runtime.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.riftwalker.aidebug.protocol.DataStorePrefsDeleteRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsListResponse
import com.riftwalker.aidebug.protocol.DataStorePrefsMutationResponse
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsValueResponse
import com.riftwalker.aidebug.protocol.PrefsEntry
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class DataStorePreferencesAdapter(
    context: Context,
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    private val appContext = context.applicationContext
    private val stores = ConcurrentHashMap<String, DataStore<Preferences>>()

    fun list(request: DataStorePrefsListRequest): DataStorePrefsListResponse {
        val entries = readPreferences(request.name)
            .asMap()
            .map { (key, value) -> value.toEntry(key.name) }
            .sortedBy { it.key }
        auditLog.recordRead("datastore.preferences.list", request.name, "success")
        return DataStorePrefsListResponse(request.name, entries)
    }

    fun get(request: DataStorePrefsGetRequest): DataStorePrefsValueResponse {
        val entry = readPreferences(request.name)
            .asMap()
            .entries
            .firstOrNull { it.key.name == request.key }
            ?.let { (_, value) -> value.toEntry(request.key) }
        auditLog.recordRead("datastore.preferences.get", "${request.name}:${request.key}", "success")
        return if (entry == null) {
            DataStorePrefsValueResponse(request.name, request.key, exists = false)
        } else {
            DataStorePrefsValueResponse(
                name = request.name,
                key = request.key,
                exists = true,
                type = entry.type,
                value = entry.value,
            )
        }
    }

    fun set(request: DataStorePrefsSetRequest): DataStorePrefsMutationResponse {
        val previous = dump(request.name)[request.key]
        val restoreToken = cleanupRegistry.register("restore datastore preferences ${request.name}:${request.key}") {
            runBlocking {
                store(request.name).edit { preferences ->
                    if (previous == null) {
                        preferences.removeAnyKey(request.key)
                    } else {
                        preferences.putAny(previous)
                    }
                }
            }
        }
        runBlocking {
            store(request.name).edit { preferences ->
                preferences.putAny(PrefsEntry(request.key, request.type ?: inferType(request.value), request.value))
            }
        }
        auditLog.recordMutation(
            tool = "datastore.preferences.set",
            target = "${request.name}:${request.key}",
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.value.toString(),
        )
        return DataStorePrefsMutationResponse(request.name, request.key, restoreToken)
    }

    fun delete(request: DataStorePrefsDeleteRequest): DataStorePrefsMutationResponse {
        val previous = dump(request.name)[request.key]
        val restoreToken = previous?.let { previousEntry ->
            cleanupRegistry.register("restore datastore preferences ${request.name}:${request.key}") {
                runBlocking {
                    store(request.name).edit { preferences ->
                        preferences.putAny(previousEntry)
                    }
                }
            }
        }
        runBlocking {
            store(request.name).edit { preferences ->
                preferences.removeAnyKey(request.key)
            }
        }
        auditLog.recordMutation("datastore.preferences.delete", "${request.name}:${request.key}", restoreToken, "success")
        return DataStorePrefsMutationResponse(request.name, request.key, restoreToken)
    }

    fun dump(name: String): Map<String, PrefsEntry> {
        return readPreferences(name).asMap().map { (key, value) ->
            key.name to value.toEntry(key.name)
        }.toMap()
    }

    fun restore(name: String, entries: Map<String, PrefsEntry>) {
        runBlocking {
            store(name).edit { preferences ->
                preferences.clear()
                entries.values.forEach { entry -> preferences.putAny(entry) }
            }
        }
    }

    private fun readPreferences(name: String): Preferences {
        return runBlocking {
            store(name).data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .first()
        }
    }

    private fun store(name: String): DataStore<Preferences> {
        require(name.matches(SAFE_NAME)) { "Invalid DataStore Preferences name: $name" }
        return stores.getOrPut(name) {
            PreferenceDataStoreFactory.create {
                appContext.preferencesDataStoreFile(name)
            }
        }
    }

    private fun MutablePreferences.putAny(entry: PrefsEntry) {
        val value = entry.value
        when (entry.type) {
            "boolean" -> this[booleanPreferencesKey(entry.key)] = value?.jsonPrimitive?.booleanOrNull ?: false
            "int" -> this[intPreferencesKey(entry.key)] =
                value?.jsonPrimitive?.intOrNull ?: value?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            "long" -> this[longPreferencesKey(entry.key)] = value?.jsonPrimitive?.longOrNull ?: 0L
            "float" -> this[floatPreferencesKey(entry.key)] = value?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
            "double" -> this[doublePreferencesKey(entry.key)] = value?.jsonPrimitive?.doubleOrNull ?: 0.0
            "stringSet" -> this[stringSetPreferencesKey(entry.key)] =
                (value as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
            else -> this[stringPreferencesKey(entry.key)] =
                value?.let { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }.orEmpty()
        }
    }

    private fun MutablePreferences.removeAnyKey(key: String) {
        remove(booleanPreferencesKey(key))
        remove(intPreferencesKey(key))
        remove(longPreferencesKey(key))
        remove(floatPreferencesKey(key))
        remove(doublePreferencesKey(key))
        remove(stringSetPreferencesKey(key))
        remove(stringPreferencesKey(key))
    }

    private fun Any?.toEntry(key: String): PrefsEntry {
        return when (this) {
            null -> PrefsEntry(key, "null", JsonNull)
            is Boolean -> PrefsEntry(key, "boolean", JsonPrimitive(this))
            is Int -> PrefsEntry(key, "int", JsonPrimitive(this))
            is Long -> PrefsEntry(key, "long", JsonPrimitive(this))
            is Float -> PrefsEntry(key, "float", JsonPrimitive(this))
            is Double -> PrefsEntry(key, "double", JsonPrimitive(this))
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
