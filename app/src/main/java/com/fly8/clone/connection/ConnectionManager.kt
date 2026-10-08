package com.fly8.clone.connection

import android.content.Context

class ConnectionManager(context: Context) {
    private val appContext = context.applicationContext
    private val store = ConnectionStore(appContext)

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
            ConnectionMethod.WIRELESS_DEBUGGING -> WirelessAdbBridge(appContext)
        }
    }

    fun wirelessBridge(): WirelessAdbBridge = WirelessAdbBridge(appContext)

    fun statusText(): String {
        return when (currentMethod()) {
            ConnectionMethod.SHIZUKU -> "Shizuku"
            ConnectionMethod.ROOT -> "Root"
            ConnectionMethod.WIRELESS_DEBUGGING -> {
                val w = wireless()
                when {
                    w.host.isNotBlank() && w.connectionPort != 0 ->
                        "Wireless debugging • " + w.host + ":" + w.connectionPort
                    w.paired ->
                        "Wireless debugging • paired • auto discovery"
                    else ->
                        "Wireless debugging • setup required"
                }
            }
        }
    }
}
