package com.fly8.clone.model

data class CloneEntry(
    val packageName: String,
    val label: String,
    val userId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val recoveryEnabled: Boolean = false,
    val recoveryIntervalMinutes: Int = 15,
    val restartOnNetworkChange: Boolean = false,
    val restartOnPublicIpChange: Boolean = false,
    val lastKnownState: SessionState = SessionState.UNKNOWN,
    val lastActionAt: Long = System.currentTimeMillis()
)

enum class SessionState {
    UNKNOWN,
    RUNNING,
    STOPPED,
    UNAVAILABLE
}
