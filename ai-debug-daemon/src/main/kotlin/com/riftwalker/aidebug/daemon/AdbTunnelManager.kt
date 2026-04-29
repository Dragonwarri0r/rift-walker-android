package com.riftwalker.aidebug.daemon

data class AdbTunnel(
    val serial: String,
    val hostPort: Int,
    val devicePort: Int,
    val direction: Direction,
) {
    enum class Direction {
        Forward,
        Reverse,
    }
}

class AdbTunnelManager(private val adb: AdbExecutor = AdbExecutor()) {
    fun forward(serial: String, hostPort: Int, devicePort: Int): AdbTunnel {
        adb.exec(
            serial = serial,
            args = listOf("forward", "tcp:$hostPort", "tcp:$devicePort"),
        )
        return AdbTunnel(serial, hostPort, devicePort, AdbTunnel.Direction.Forward)
    }

    fun reverse(serial: String, hostPort: Int, devicePort: Int): AdbTunnel {
        adb.exec(
            serial = serial,
            args = listOf("reverse", "tcp:$devicePort", "tcp:$hostPort"),
        )
        return AdbTunnel(serial, hostPort, devicePort, AdbTunnel.Direction.Reverse)
    }
}
