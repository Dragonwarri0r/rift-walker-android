package com.riftwalker.aidebug.daemon

class AdbDeviceSelector(private val adb: AdbExecutor = AdbExecutor()) {
    fun listDevices(): List<AdbDevice> {
        val output = adb.exec(listOf("devices"))
        return output.lines()
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                val serial = parts.getOrNull(0) ?: return@mapNotNull null
                val state = parts.getOrNull(1) ?: "unknown"
                AdbDevice(serial, state)
            }
    }

    fun select(serial: String?): AdbDevice {
        val devices = listDevices().filter { it.state == "device" }
        if (serial != null) {
            return devices.firstOrNull { it.serial == serial }
                ?: error("ADB device '$serial' is not connected")
        }
        return when (devices.size) {
            0 -> error("No connected ADB devices")
            1 -> devices.first()
            else -> error("Multiple ADB devices connected; pass a serial")
        }
    }
}
