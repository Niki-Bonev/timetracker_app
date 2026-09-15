package com.nikibonev.tempo.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nikibonev.tempo.MainActivity
import com.nikibonev.tempo.R
import com.nikibonev.tempo.data.model.ActiveTimer
import com.nikibonev.tempo.data.model.AppSettings
import com.nikibonev.tempo.util.formatDuration

class TimerNotificationManager(private val context: Context) {
    init { createChannel() }

    fun render(active: ActiveTimer?, settings: AppSettings) {
        if (!settings.showTimerNotification || active == null || !canPostNotifications()) { cancel(); return }
        val notificationManager = NotificationManagerCompat.from(context)
        val openApp = PendingIntent.getActivity(context, 100, Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = if (active.isPaused) ACTION_RESUME else ACTION_PAUSE
        val actionLabel = if (active.isPaused) "Resume" else "Pause"
        val actionIcon = if (active.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val actionIntent = PendingIntent.getBroadcast(context, if (active.isPaused) 201 else 202, Intent(context, TimerActionReceiver::class.java).apply { this.action = action; putExtra(EXTRA_OWNER_ID, active.session.ownerId) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val finishIntent = PendingIntent.getBroadcast(context, 203, Intent(context, TimerActionReceiver::class.java).apply { action = ACTION_FINISH; putExtra(EXTRA_OWNER_ID, active.session.ownerId) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val currentInterval = active.intervals.lastOrNull { !it.deleted && it.endedAt == null }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (active.isPaused) "Break · ${active.project.name}" else active.project.name)
            .setContentText(if (active.isPaused) "Focused ${formatDuration(active.workMillis(), compact = true)} so far" else active.session.intention.ifBlank { "Tracking focused work" })
            .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(actionIcon, actionLabel, actionIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Finish", finishIntent)
        if (currentInterval != null) builder.setWhen(currentInterval.startedAt).setUsesChronometer(true).setShowWhen(true)
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Active timer", NotificationManager.IMPORTANCE_LOW).apply { description = "Shows an ongoing work or break timer with quick controls."; setShowBadge(false) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.nikibonev.tempo.PAUSE"
        const val ACTION_RESUME = "com.nikibonev.tempo.RESUME"
        const val ACTION_FINISH = "com.nikibonev.tempo.FINISH"
        const val EXTRA_OWNER_ID = "owner_id"
        private const val CHANNEL_ID = "active_timer"
        private const val NOTIFICATION_ID = 4201
    }
}
