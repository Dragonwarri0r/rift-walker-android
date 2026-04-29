package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PrefsListRequest(
    val fileName: String,
)

@Serializable
data class PrefsEntry(
    val key: String,
    val type: String,
    val value: JsonElement? = null,
)

@Serializable
data class PrefsListResponse(
    val fileName: String,
    val entries: List<PrefsEntry>,
)

@Serializable
data class PrefsGetRequest(
    val fileName: String,
    val key: String,
)

@Serializable
data class PrefsValueResponse(
    val fileName: String,
    val key: String,
    val exists: Boolean,
    val type: String? = null,
    val value: JsonElement? = null,
)

@Serializable
data class PrefsSetRequest(
    val fileName: String,
    val key: String,
    val value: JsonElement,
    val type: String? = null,
)

@Serializable
data class PrefsDeleteRequest(
    val fileName: String,
    val key: String,
)

@Serializable
data class PrefsMutationResponse(
    val fileName: String,
    val key: String,
    val restoreToken: String? = null,
)

@Serializable
data class DataStorePrefsListRequest(
    val name: String,
)

@Serializable
data class DataStorePrefsListResponse(
    val name: String,
    val entries: List<PrefsEntry>,
)

@Serializable
data class DataStorePrefsGetRequest(
    val name: String,
    val key: String,
)

@Serializable
data class DataStorePrefsValueResponse(
    val name: String,
    val key: String,
    val exists: Boolean,
    val type: String? = null,
    val value: JsonElement? = null,
)

@Serializable
data class DataStorePrefsSetRequest(
    val name: String,
    val key: String,
    val value: JsonElement,
    val type: String? = null,
)

@Serializable
data class DataStorePrefsDeleteRequest(
    val name: String,
    val key: String,
)

@Serializable
data class DataStorePrefsMutationResponse(
    val name: String,
    val key: String,
    val restoreToken: String? = null,
)

@Serializable
data class SqlQueryRequest(
    val databaseName: String,
    val sql: String,
    val args: List<String> = emptyList(),
    val limit: Int = 100,
)

@Serializable
data class SqlQueryResponse(
    val databaseName: String,
    val columns: List<String>,
    val rows: List<Map<String, JsonElement?>>,
    val truncated: Boolean,
)

@Serializable
data class SqlExecRequest(
    val databaseName: String,
    val sql: String,
    val args: List<String> = emptyList(),
)

@Serializable
data class SqlExecResponse(
    val databaseName: String,
    val restoreToken: String? = null,
)

@Serializable
data class StorageSnapshotRequest(
    val name: String? = null,
    val prefsFiles: List<String> = emptyList(),
    val databaseNames: List<String> = emptyList(),
    val dataStorePreferenceNames: List<String> = emptyList(),
)

@Serializable
data class StorageSnapshotResponse(
    val snapshotId: String,
    val name: String? = null,
    val prefsFiles: List<String>,
    val databaseNames: List<String>,
    val dataStorePreferenceNames: List<String> = emptyList(),
    val createdAtEpochMs: Long,
)

@Serializable
data class StorageRestoreRequest(
    val snapshotId: String,
)

@Serializable
data class StorageRestoreResponse(
    val snapshotId: String,
    val restoredPrefsFiles: List<String>,
    val restoredDatabaseNames: List<String>,
    val restoredDataStorePreferenceNames: List<String> = emptyList(),
)
