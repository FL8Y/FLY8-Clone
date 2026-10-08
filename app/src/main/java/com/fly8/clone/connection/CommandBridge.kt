package com.fly8.clone.connection

data class BridgeResult(
    val code: Int,
    val output: String = "",
    val error: String = ""
) {
    val ok: Boolean get() = code == 0
    val message: String get() = error.ifBlank { output }.ifBlank { "Command failed." }
}

interface CommandBridge : AutoCloseable {
    val method: ConnectionMethod
    fun check(): BridgeResult
    fun run(command: String): BridgeResult
    override fun close() = Unit
}
