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

                val bridgeManager = ConnectionManager(appContext)
                val backend = NativeProfileBackend(bridgeManager.bridge())
                val result = backend.launch(clone)

                if (result.isSuccess) {
                    activityLog.add("Scheduled recovery: " + clone.label + " • " + bridgeManager.statusText())
                } else {
                    activityLog.add("Recovery failed: " + clone.label + " • " + (result.exceptionOrNull()?.message ?: "unknown error"))
                }

                RecoveryScheduler.schedule(appContext, clone)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
