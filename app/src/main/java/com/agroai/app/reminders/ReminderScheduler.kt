package com.agroai.app.reminders

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.agroai.app.MainActivity
import com.agroai.app.domain.CalendarActivity
import com.agroai.app.domain.FarmAlert

class ReminderScheduler(private val context: Context) {
    fun schedule(activity: CalendarActivity) {
        if (!activity.reminderEnabled || activity.completed || activity.scheduledAt <= System.currentTimeMillis()) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, activity.scheduledAt, pending(activity.id, activity.title, activity.notes))
    }

    fun schedule(alert: FarmAlert) {
        if (alert.read || alert.scheduledAt <= System.currentTimeMillis()) return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            alert.scheduledAt,
            pending(alert.id, alert.title, alert.message),
        )
    }

    fun cancel(id: String) = context.getSystemService(AlarmManager::class.java).cancel(pending(id, "", ""))

    private fun pending(id: String, title: String, notes: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("notes", notes)
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "farm_calendar"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channelId, "Farm calendar", NotificationManager.IMPORTANCE_DEFAULT))
        val openIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Farm activity" }
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channelId) else @Suppress("DEPRECATION") Notification.Builder(context)
        manager.notify(intent.getStringExtra("id").orEmpty().hashCode(), builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(intent.getStringExtra("notes").orEmpty()).setContentIntent(openIntent).setAutoCancel(true).build())
    }
}
