package com.fly8.clone.connection

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.runBlocking

object WirelessPairing {
    fun pair(host: String, port: Int, code: String): Result<Unit> {
        if (host.isBlank()) return Result.failure(IllegalArgumentException("Enter the Wireless debugging address."))
        if (port !in 1..65535) return Result.failure(IllegalArgumentException("Enter a valid pairing port."))
        if (!code.matches(Regex("\\d{6}"))) {
            return Result.failure(IllegalArgumentException("Enter the current six-digit pairing code."))
        }

        return runCatching {
            runBlocking { Kadb.pair(host, port, code) }
            Unit
        }
    }

    fun test(host: String, port: Int): Result<Unit> {
        if (host.isBlank() || port !in 1..65535) {
            return Result.failure(IllegalArgumentException("Enter a valid connection address and port."))
        }
        return runCatching {
            Kadb.create(host, port, connectTimeout = 7000, socketTimeout = 7000).use {
                val result = it.shell("echo FLY8_CONNECTED")
                check(result.exitCode == 0) { result.allOutput }
                check(result.allOutput.contains("FLY8_CONNECTED")) { "ADB connection did not return the expected response." }
            }
        }
    }
}
