package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class DebugStateDescriptor(
    val path: String,
    val schema: JsonObject,
    val mutable: Boolean,
    val nullable: Boolean = true,
    val description: String,
    val tags: List<String> = emptyList(),
    val pii: String = "none",
    val resetPolicy: String = "none",
)

@Serializable
data class StateListRequest(
    val query: String? = null,
    val tag: String? = null,
)

@Serializable
data class StateListResponse(
    val states: List<DebugStateDescriptor>,
)

@Serializable
data class StateGetRequest(
    val path: String,
)

@Serializable
data class StateValueResponse(
    val path: String,
    val value: JsonElement? = null,
    val mutable: Boolean,
)

@Serializable
data class StateSetRequest(
    val path: String,
    val value: JsonElement,
)

@Serializable
data class StateResetRequest(
    val path: String,
)

@Serializable
data class StateMutationResponse(
    val path: String,
    val restoreToken: String? = null,
)

@Serializable
data class StateSnapshotRequest(
    val name: String? = null,
    val paths: List<String>? = null,
)

@Serializable
data class StateSnapshotResponse(
    val snapshotId: String,
    val name: String? = null,
    val paths: List<String>,
    val createdAtEpochMs: Long,
)

@Serializable
data class StateRestoreRequest(
    val snapshotId: String,
)

@Serializable
data class StateRestoreResponse(
    val snapshotId: String,
    val restored: List<String>,
    val skipped: List<String> = emptyList(),
)

@Serializable
data class StateDiffRequest(
    val snapshotId: String,
)

@Serializable
data class StateDiffEntry(
    val path: String,
    val before: JsonElement? = null,
    val after: JsonElement? = null,
    val changed: Boolean,
)

@Serializable
data class StateDiffResponse(
    val snapshotId: String,
    val diffs: List<StateDiffEntry>,
)

@Serializable
data class DebugActionDescriptor(
    val path: String,
    val inputSchema: JsonObject,
    val description: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class ActionListRequest(
    val query: String? = null,
    val tag: String? = null,
)

@Serializable
data class ActionListResponse(
    val actions: List<DebugActionDescriptor>,
)

@Serializable
data class ActionInvokeRequest(
    val path: String,
    val input: JsonElement? = null,
)

@Serializable
data class ActionInvokeResponse(
    val path: String,
    val result: JsonElement? = null,
)
