package com.fly8.clone.connection

import android.content.Context

object WirelessPairing {
    fun pair(context: Context, host: String, port: Int, code: String): Result<Unit> {
        val bridge = WirelessAdbBridge(context)
        val result = bridge.pair(host, port, code)
        bridge.close()
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.message))
        }
    }

    fun test(context: Context, host: String, port: Int): Result<Unit> {
        // Do not close the singleton ADB manager here. The reference keeps a paired
        // control channel reusable until the connection is explicitly lost.
        val bridge = WirelessAdbBridge(context)
        val result = bridge.connect(host, port)
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.message))
        }
    }

    fun autoConnect(context: Context): Result<Unit> {
        val bridge = WirelessAdbBridge(context)
        val result = bridge.connectAutomatically()
        return if (result.ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.message))
        }
    }
}
