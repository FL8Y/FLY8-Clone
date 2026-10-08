package com.fly8.clone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                CloneStore(appContext).load()
                    .filter { it.recoveryEnabled }
                    .forEach { RecoveryScheduler.schedule(appContext, it) }
                ActivityLogStore(appContext).add("Recovery schedules restored after boot")
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
