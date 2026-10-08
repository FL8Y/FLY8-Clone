package com.fly8.clone

import com.fly8.clone.connection.CommandBridge
import com.fly8.clone.model.CloneEntry

object SessionProbe {
    fun isRunning(bridge: CommandBridge, clone: CloneEntry): Boolean {
        val uidResult = bridge.run(
            "pm list packages -U --user " + clone.userId + " " + clone.packageName
        )
        if (!uidResult.ok) return false

        val uid = Regex("""uid:(\d+)""")
            .find(uidResult.output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return false

        val state = bridge.run("cmd activity get-uid-state " + uid)
        if (!state.ok) return false

        val normalized = state.output.lowercase()
        if ("gone" in normalized || "nonexistent" in normalized || "does not exist" in normalized) {
            return false
        }

        return normalized.contains("top") ||
            normalized.contains("foreground") ||
            normalized.contains("background") ||
            normalized.contains("cached") ||
            normalized.contains("service") ||
            normalized.contains("perceptible")
    }
}
