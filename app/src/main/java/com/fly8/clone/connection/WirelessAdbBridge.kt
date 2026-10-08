package com.fly8.clone.connection

import android.content.Context

class WirelessAdbBridge(context: Context) : CommandBridge {
    override val method = ConnectionMethod.WIRELESS_DEBUGGING
    private val manager = Fly8AdbConnectionManager.get(context)

    override fun check(): BridgeResult {
        if (manager.isConnected) {
            return BridgeResult(0, output = "Wireless debugging connected.")
        }

        return manager.connectAutomatically().fold(
            onSuccess = { BridgeResult(0, output = "Wireless debugging connected.") },
            onFailure = {
                BridgeResult(
                    -1,
                    error = it.message ?: "Could not connect to Wireless debugging."
                )
            }
        )
    }

    fun pair(host: String, port: Int, code: String): BridgeResult {
        return manager.pairDevice(host, port, code).fold(
            onSuccess = { BridgeResult(0, output = "Wireless debugging pairing completed.") },
            onFailure = {
                BridgeResult(
                    -1,
                    error = it.message ?: "Wireless debugging pairing failed."
                )
            }
        )
    }

    fun connect(host: String, port: Int): BridgeResult {
        return manager.connectManually(host, port).fold(
            onSuccess = { BridgeResult(0, output = "Wireless debugging connected.") },
            onFailure = {
                BridgeResult(
                    -1,
                    error = it.message ?: "Wireless debugging connection failed."
                )
            }
        )
    }

    fun connectAutomatically(): BridgeResult {
        return manager.connectAutomatically().fold(
            onSuccess = { BridgeResult(0, output = "Wireless debugging connected.") },
            onFailure = {
                BridgeResult(
                    -1,
                    error = it.message ?: "Automatic Wireless debugging discovery failed."
                )
            }
        )
    }

    override fun run(command: String): BridgeResult = manager.runCommand(command)

    override fun close() {
        manager.disconnectQuietly()
    }
}
