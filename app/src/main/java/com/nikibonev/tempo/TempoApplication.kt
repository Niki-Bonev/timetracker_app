package com.nikibonev.tempo

import android.app.Application

class TempoApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
    override fun onCreate() {
        super.onCreate()
        graph
    }
}
