package com.fly8.clone.connection

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

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

    fun bridgeFor(method: ConnectionMethod): CommandBridge = when (method) {
        ConnectionMethod.SHIZUKU -> ShizukuBridge()
        ConnectionMethod.ROOT -> RootBridge()
        ConnectionMethod.WIRELESS_DEBUGGING -> WirelessAdbBridge(appContext)
    }

    fun bridge(): CommandBridge {
        return bridgeFor(currentMethod())
    }

    fun wirelessBridge(): WirelessAdbBridge = WirelessAdbBridge(appContext)

    fun isReady(): Boolean {
        return when (currentMethod()) {
            ConnectionMethod.WIRELESS_DEBUGGING ->
                Fly8AdbConnectionManager.get(appContext).isConnected
            ConnectionMethod.SHIZUKU ->
                Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ConnectionMethod.ROOT ->
                storeRootChecked()
        }
    }

    fun markRootVerified() { store.contextPrefs().edit().putBoolean("root_verified", true).apply() }

    fun checkSelected(): BridgeResult {
        return when (currentMethod()) {
            ConnectionMethod.WIRELESS_DEBUGGING ->
                wirelessBridge().check()
            ConnectionMethod.SHIZUKU ->
                bridge().use { it.check() }
            ConnectionMethod.ROOT ->
                bridge().use { it.check() }
        }
    }

    fun statusText(): String {
        return when (currentMethod()) {
            ConnectionMethod.SHIZUKU ->
                if (isReady()) "Shizuku • connected" else "Shizuku • not connected"
            ConnectionMethod.ROOT ->
                if (isReady()) "Root • connected" else "Root • check required"
            ConnectionMethod.WIRELESS_DEBUGGING -> {
                val w = wireless()
                when {
                    Fly8AdbConnectionManager.get(appContext).isConnected ->
                        "Wireless debugging • connected"
                    w.paired && w.host.isNotBlank() && w.connectionPort != 0 ->
                        "Wireless debugging • paired • saved endpoint"
                    w.paired ->
                        "Wireless debugging • paired • auto discovery"
                    else ->
                        "Wireless debugging • setup required"
                }
            }
        }
    }

    private fun storeRootChecked(): Boolean =
        store.contextPrefs().getBoolean("root_verified", false)
}
