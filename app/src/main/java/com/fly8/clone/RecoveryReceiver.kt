package com.fly8.clone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fly8.clone.backend.NativeProfileBackend
import com.fly8.clone.connection.ConnectionManager

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = RecoveryScheduler.userId(intent)
        if (userId < 0) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        Thread {
            try {
                val store = CloneStore(appContext)
                val activityLog = ActivityLogStore(appContext)
                val clone = store.load().firstOrNull { it.userId == userId } ?: return@Thread

                if (!clone.recoveryEnabled) {
                    RecoveryScheduler.cancel(appContext, clone)
                    return@Thread
                }

                val manager = ConnectionManager(appContext)
                val bridge = manager.bridge()

                try {
                    val readiness = bridge.check()
                    if (!readiness.ok) {
                        activityLog.add("Recovery postponed: controls unavailable for " + clone.label)
                        RecoveryScheduler.schedule(appContext, clone)
                        return@Thread
                    }

                    if (SessionProbe.isRunning(bridge, clone)) {
                        activityLog.add("Recovery skipped: " + clone.label + " is still running")
                        RecoveryScheduler.schedule(appContext, clone)
                        return@Thread
                    }

                    val backend = NativeProfileBackend(bridge)
                    val started = backend.launch(clone)
                    if (started.isFailure) {
                        activityLog.add(
                            "Recovery failed: " + clone.label + " • " +
                                (started.exceptionOrNull()?.message ?: "launch failed")
                        )
                        RecoveryScheduler.schedule(appContext, clone)
                        return@Thread
                    }

                    activityLog.add("Recovery restart: " + clone.label)

                    if (clone.rejoinTarget.isNotBlank()) {
                        Thread.sleep(2500)
                        val rejoin = RejoinLauncher.launch(bridge, clone)
                        if (rejoin.isSuccess) {
                            activityLog.add("Recovery rejoin requested: " + clone.label)
                        } else {
                            activityLog.add(
                                "Recovery rejoin failed: " + clone.label + " • " +
                                    (rejoin.exceptionOrNull()?.message ?: "invalid target")
                            )
                        }
                    }

                    RecoveryScheduler.schedule(appContext, clone)
                } finally {
                    bridge.close()
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
