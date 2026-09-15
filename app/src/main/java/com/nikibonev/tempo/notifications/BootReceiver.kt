package com.nikibonev.tempo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nikibonev.tempo.TempoApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        (context.applicationContext as? TempoApplication)?.graph?.refreshNotificationSoon()
    }
}
