package com.fly8.clone

import com.fly8.clone.connection.CommandBridge
import com.fly8.clone.model.CloneEntry

object RejoinLauncher {
    fun launch(bridge: CommandBridge, clone: CloneEntry): Result<Unit> {
        val target = RejoinTarget.normalize(clone.rejoinTarget)
            ?: return Result.failure(IllegalArgumentException("No valid Roblox rejoin target is configured."))

        val command =
            "am start --user " + clone.userId +
                " -a android.intent.action.VIEW -d " + RejoinTarget.shellQuote(target)

        val result = bridge.run(command)
        return if (result.ok) Result.success(Unit)
        else Result.failure(IllegalStateException(result.message))
    }
}
