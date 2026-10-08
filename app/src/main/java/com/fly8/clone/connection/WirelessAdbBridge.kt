package com.fly8.clone.connection

import com.flyfishxu.kadb.Kadb

data class WirelessAdbConfig(
    val host: String,
    val port: Int
)

class WirelessAdbBridge(private val config: WirelessAdbConfig) : CommandBridge {
    override val method = ConnectionMethod.WIRELESS_DEBUGGING

    override fun check(): BridgeResult {
        if (config.host.isBlank() || config.port !in 1..65535) {
            return BridgeResult(-20, error = "Wireless debugging connection details are incomplete.")
        }
        return run("echo FLY8_CONNECTED")
    }

    override fun run(command: String): BridgeResult {
        return try {
            Kadb.create(
                host = config.host,
                port = config.port,
                connectTimeout = 7000,
                socketTimeout = 7000
            ).use { adb ->
                val result = adb.shell(command)
                BridgeResult(result.exitCode, result.output, result.errorOutput)
            }
        } catch (t: Throwable) {
            BridgeResult(-1, error = t.message ?: "Could not connect to Wireless debugging.")
        }
    }
}
