package com.fly8.clone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fly8.clone.backend.ShizukuCloneBackend

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
                val clone = store.load().firstOrNull { it.userId == userId }
                    ?: return@Thread

                if (!clone.recoveryEnabled) {
                    RecoveryScheduler.cancel(appContext, clone)
                    return@Thread
                }

                val backend = ShizukuCloneBackend()
                val result = backend.launch(clone)
                if (result.isSuccess) {
                    activityLog.add("Scheduled recovery: " + clone.label)
                } else {
                    activityLog.add("Recovery failed: " + clone.label)
                }

                RecoveryScheduler.schedule(appContext, clone)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
