package com.fly8.clone.connection

import android.content.Context

class ConnectionManager(context: Context) {
    private val store = ConnectionStore(context.applicationContext)

    fun currentMethod(): ConnectionMethod = store.method()

    fun setMethod(method: ConnectionMethod) {
        store.setMethod(method)
    }

    fun wireless(): WirelessPairingState = store.wireless()

    fun saveWireless(state: WirelessPairingState) {
        store.saveWireless(state)
    }

    fun bridge(): CommandBridge {
        return when (currentMethod()) {
            ConnectionMethod.SHIZUKU -> ShizukuBridge()
            ConnectionMethod.ROOT -> RootBridge()
            ConnectionMethod.WIRELESS_DEBUGGING -> {
                val state = wireless()
                WirelessAdbBridge(
                    WirelessAdbConfig(state.host, state.connectionPort)
                )
            }
        }
    }

    fun statusText(): String {
        return when (currentMethod()) {
            ConnectionMethod.SHIZUKU -> "Shizuku"
            ConnectionMethod.ROOT -> "Root"
            ConnectionMethod.WIRELESS_DEBUGGING -> {
                val w = wireless()
                if (w.host.isBlank() || w.connectionPort == 0) "Wireless debugging • setup required"
                else "Wireless debugging • " + w.host + ":" + w.connectionPort
            }
        }
    }
}
