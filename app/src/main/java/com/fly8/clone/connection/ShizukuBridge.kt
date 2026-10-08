package com.fly8.clone.connection

import com.fly8.clone.Shell
import rikka.shizuku.Shizuku

class ShizukuBridge : CommandBridge {
    override val method = ConnectionMethod.SHIZUKU

    override fun check(): BridgeResult {
        return runCatching {
            when {
                !Shizuku.pingBinder() -> BridgeResult(-10, error = "Shizuku is not running.")
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                    BridgeResult(-11, error = "FLY8 does not have Shizuku permission.")
                else -> BridgeResult(0, output = "Shizuku connected.")
            }
        }.getOrElse { BridgeResult(-1, error = it.message ?: "Shizuku check failed.") }
    }

    override fun run(command: String): BridgeResult {
        val r = Shell.run(command)
        return BridgeResult(r.code, r.out, r.err)
    }
}
