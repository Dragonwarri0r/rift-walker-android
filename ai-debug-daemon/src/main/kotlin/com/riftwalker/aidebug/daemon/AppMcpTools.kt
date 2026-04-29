package com.riftwalker.aidebug.daemon

import com.riftwalker.aidebug.protocol.AdbForwardRequest
import com.riftwalker.aidebug.protocol.AdbForwardResponse
import com.riftwalker.aidebug.protocol.AppCommandResponse
import com.riftwalker.aidebug.protocol.AppForceStopRequest
import com.riftwalker.aidebug.protocol.AppLaunchRequest
import com.riftwalker.aidebug.protocol.DeviceInfo
import com.riftwalker.aidebug.protocol.DeviceListRequest
import com.riftwalker.aidebug.protocol.DeviceListResponse
import com.riftwalker.aidebug.protocol.ProtocolJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class AppMcpTools(
    private val adb: AdbExecutor = AdbExecutor(),
) {
    private val devices = AdbDeviceSelector(adb)
    private val tunnels = AdbTunnelManager(adb)

    fun registerInto(registry: DaemonToolRegistry) {
        registry.register(
            name = "device.list",
            description = "List connected ADB devices",
            inputSchema = objectSchema(),
        ) { args ->
            val request = args?.let { ProtocolJson.json.decodeFromJsonElement<DeviceListRequest>(it) }
                ?: DeviceListRequest()
            val listed = devices.listDevices()
                .filter { request.includeOffline || it.state == "device" }
                .map { DeviceInfo(serial = it.serial, state = it.state) }
            ProtocolJson.json.encodeToJsonElement(DeviceListResponse(listed))
        }
        registry.register(
            name = "adb.forward",
            description = "Forward a localhost TCP port to the app runtime port on a selected device",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<AdbForwardRequest>(
                args ?: error("adb.forward requires arguments"),
            )
            val selected = devices.select(request.serial)
            if (request.removeExisting) {
                runCatching {
                    adb.exec(listOf("forward", "--remove", "tcp:${request.hostPort}"), serial = selected.serial)
                }
            }
            val tunnel = tunnels.forward(selected.serial, request.hostPort, request.devicePort)
            ProtocolJson.json.encodeToJsonElement(
                AdbForwardResponse(
                    serial = tunnel.serial,
                    hostPort = tunnel.hostPort,
                    devicePort = tunnel.devicePort,
                ),
            )
        }
        registry.register(
            name = "app.forceStop",
            description = "Force-stop a package through ADB",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<AppForceStopRequest>(
                args ?: error("app.forceStop requires arguments"),
            )
            val selected = devices.select(request.serial)
            val output = adb.exec(
                serial = selected.serial,
                args = listOf("shell", "am", "force-stop", request.packageName),
            )
            ProtocolJson.json.encodeToJsonElement(
                AppCommandResponse(
                    serial = selected.serial,
                    packageName = request.packageName,
                    output = output.trim(),
                ),
            )
        }
        registry.register(
            name = "app.launch",
            description = "Launch an Android activity with simple typed extras through ADB",
            inputSchema = objectSchema(),
        ) { args ->
            val request = ProtocolJson.json.decodeFromJsonElement<AppLaunchRequest>(
                args ?: error("app.launch requires arguments"),
            )
            val selected = devices.select(request.serial)
            val command = buildList {
                add("shell")
                add("am")
                add("start")
                if (request.wait) add("-W")
                request.action?.let {
                    add("-a")
                    add(it)
                }
                add("-n")
                add(request.activity)
                request.extras.forEach { (key, value) ->
                    addAll(extraArgs(key, value))
                }
            }
            val output = adb.exec(serial = selected.serial, args = command)
            ProtocolJson.json.encodeToJsonElement(
                AppCommandResponse(
                    serial = selected.serial,
                    activity = request.activity,
                    output = output.trim(),
                ),
            )
        }
    }

    private fun extraArgs(key: String, value: JsonElement): List<String> {
        require(value !is JsonNull) { "app.launch extras do not support null values: $key" }
        require(value is JsonPrimitive) { "app.launch extras only support primitive values: $key" }
        return when {
            value.isString -> listOf("--es", key, value.content)
            value.booleanOrNull != null -> listOf("--ez", key, value.booleanOrNull.toString())
            value.intOrNull != null -> listOf("--ei", key, value.intOrNull.toString())
            value.longOrNull != null -> listOf("--el", key, value.longOrNull.toString())
            else -> listOf("--es", key, value.content)
        }
    }

    private fun objectSchema() = buildJsonObject {
        put("type", "object")
        put("additionalProperties", true)
    }
}
