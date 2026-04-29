package com.riftwalker.aidebug.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DeviceListRequest(
    val includeOffline: Boolean = false,
)

@Serializable
data class DeviceInfo(
    val serial: String,
    val state: String,
)

@Serializable
data class DeviceListResponse(
    val devices: List<DeviceInfo>,
)

@Serializable
data class AdbForwardRequest(
    val serial: String? = null,
    val hostPort: Int,
    val devicePort: Int,
    val removeExisting: Boolean = true,
)

@Serializable
data class AdbForwardResponse(
    val serial: String,
    val hostPort: Int,
    val devicePort: Int,
)

@Serializable
data class AppForceStopRequest(
    val serial: String? = null,
    val packageName: String,
)

@Serializable
data class AppCommandResponse(
    val serial: String,
    val packageName: String? = null,
    val activity: String? = null,
    val output: String,
)

@Serializable
data class AppLaunchRequest(
    val serial: String? = null,
    val activity: String,
    val action: String? = null,
    val extras: Map<String, JsonElement> = emptyMap(),
    val wait: Boolean = false,
)
