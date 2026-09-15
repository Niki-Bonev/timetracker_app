package com.nikibonev.tempo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nikibonev.tempo.TempoApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ownerId = intent.getStringExtra(TimerNotificationManager.EXTRA_OWNER_ID) ?: return
        val pending = goAsync()
        val graph = (context.applicationContext as TempoApplication).graph
        graph.appScope.launch(Dispatchers.IO) {
            try {
                when (intent.action) {
                    TimerNotificationManager.ACTION_PAUSE -> graph.timeRepository.pause(ownerId)
                    TimerNotificationManager.ACTION_RESUME -> graph.timeRepository.resume(ownerId)
                    TimerNotificationManager.ACTION_FINISH -> graph.timeRepository.finish(ownerId)
                }
            } finally { pending.finish() }
        }
    }
}
