package com.fly8.clone.model

enum class ProfileKind {
    CLONE_PROFILE,
    SECONDARY_USER
}

data class CloneEntry(
    val packageName: String,
    val label: String,
    val userId: Int,
    val profileKind: ProfileKind = ProfileKind.CLONE_PROFILE,
    val createdAt: Long = System.currentTimeMillis(),
    val recoveryEnabled: Boolean = false,
    val recoveryIntervalMinutes: Int = 15,
    val restartOnNetworkChange: Boolean = false,
    val restartOnPublicIpChange: Boolean = false,
    val rejoinTarget: String = "",
    val stalledDetectionEnabled: Boolean = false,
    val stalledTimeoutMinutes: Int = 10,
    val lastKnownState: SessionState = SessionState.UNKNOWN,
    val lastActionAt: Long = System.currentTimeMillis()
)

enum class SessionState {
    UNKNOWN,
    RUNNING,
    STOPPED,
    UNAVAILABLE
}
