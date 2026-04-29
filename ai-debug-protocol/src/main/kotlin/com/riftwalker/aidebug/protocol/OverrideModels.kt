package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OverrideEntry(
    val key: String,
    val value: JsonElement,
    val expiresAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class OverrideSetRequest(
    val key: String,
    val value: JsonElement,
    val ttlMs: Long? = null,
)

@Serializable
data class OverrideGetRequest(
    val key: String,
)

@Serializable
data class OverrideValueResponse(
    val key: String,
    val exists: Boolean,
    val value: JsonElement? = null,
    val expiresAtEpochMs: Long? = null,
)

@Serializable
data class OverrideClearRequest(
    val key: String? = null,
)

@Serializable
data class OverrideClearResponse(
    val cleared: Int,
)

@Serializable
data class OverrideListResponse(
    val overrides: List<OverrideEntry>,
)
