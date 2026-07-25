package com.yeqi.pet

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

class AppDetector(private val context: Context) {

    private var timer: Timer? = null
    private var lastApp: String = ""
    private var sync: SupabaseSync? = null

    fun start(sync: SupabaseSync? = null) {
        this.sync = sync
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val current = getForegroundApp()
                if (current.isNotEmpty() && current != lastApp) {
                    lastApp = current
                    onAppChanged(current)
                }
            }
        }, 0, 3000)
    }

    private fun getForegroundApp(): String {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
            as? UsageStatsManager ?: return ""
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 5000, now)
        val event = UsageEvents.Event()
        var foreground = ""
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foreground = event.packageName
            }
        }
        return foreground
    }

    private fun onAppChanged(packageName: String) {
        sync?.reportApp(packageName)
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }
}
