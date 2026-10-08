package com.fly8.clone.connection

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class RootBridge : CommandBridge {
    override val method = ConnectionMethod.ROOT

    override fun check(): BridgeResult = run("id")

    override fun run(command: String): BridgeResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val out = StringBuilder()
            val err = StringBuilder()

            val outReader = Thread {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { out.append(it).append('\n') }
                }
            }
            val errReader = Thread {
                BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                    lines.forEach { err.append(it).append('\n') }
                }
            }
            outReader.start()
            errReader.start()

            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return BridgeResult(124, out.toString().trim(), "Root command timed out.")
            }

            outReader.join(500)
            errReader.join(500)
            BridgeResult(process.exitValue(), out.toString().trim(), err.toString().trim())
        } catch (t: Throwable) {
            BridgeResult(-1, error = t.message ?: "Root access is unavailable.")
        }
    }
}
