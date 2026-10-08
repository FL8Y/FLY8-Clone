package com.fly8.clone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fly8.clone.backend.ShizukuCloneBackend

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userId = RecoveryScheduler.userId(intent)
        if (userId < 0) return
        val store = CloneStore(context)
        val clones = store.load()
        val clone = clones.firstOrNull { it.userId == userId } ?: return
        if (!clone.recoveryEnabled) return

        Thread {
            val backend = ShizukuCloneBackend()
            backend.launch(clone)
            RecoveryScheduler.schedule(context, clone)
        }.start()
    }
}
