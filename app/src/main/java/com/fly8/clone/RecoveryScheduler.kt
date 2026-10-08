package com.fly8.clone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fly8.clone.model.CloneEntry

object RecoveryScheduler {
    private const val EXTRA_USER_ID = "user_id"
    private const val REQUEST_BASE = 8100

    fun schedule(context: Context, clone: CloneEntry) {
        if (!clone.recoveryEnabled) {
            cancel(context, clone)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, RecoveryReceiver::class.java).putExtra(EXTRA_USER_ID, clone.userId)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + clone.userId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val delay = clone.recoveryIntervalMinutes.coerceIn(5, 1440) * 60_000L
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pending)
    }

    fun cancel(context: Context, clone: CloneEntry) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, RecoveryReceiver::class.java).putExtra(EXTRA_USER_ID, clone.userId)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + clone.userId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarm.cancel(pending)
        pending.cancel()
    }

    fun userId(intent: Intent): Int = intent.getIntExtra(EXTRA_USER_ID, -1)
}
