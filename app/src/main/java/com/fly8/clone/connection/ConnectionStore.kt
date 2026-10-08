package com.fly8.clone.connection

import android.content.Context

data class WirelessPairingState(
    val host: String = "",
    val pairingPort: Int = 0,
    val connectionPort: Int = 0,
    val paired: Boolean = false
)

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("fly8_connection", Context.MODE_PRIVATE)

    fun contextPrefs() = prefs

    fun method(): ConnectionMethod =
        runCatching {
            ConnectionMethod.valueOf(
                prefs.getString("method", ConnectionMethod.SHIZUKU.name)
                    ?: ConnectionMethod.SHIZUKU.name
            )
        }.getOrDefault(ConnectionMethod.SHIZUKU)

    fun setMethod(method: ConnectionMethod) =
        prefs.edit().putString("method", method.name).apply()

    fun wireless(): WirelessPairingState = WirelessPairingState(
        host = prefs.getString("host", "") ?: "",
        pairingPort = prefs.getInt("pairing_port", 0),
        connectionPort = prefs.getInt("connection_port", 0),
        paired = prefs.getBoolean("paired", false)
    )

    fun saveWireless(state: WirelessPairingState) {
        prefs.edit()
            .putString("host", state.host)
            .putInt("pairing_port", state.pairingPort)
            .putInt("connection_port", state.connectionPort)
            .putBoolean("paired", state.paired)
            .apply()
    }
}
