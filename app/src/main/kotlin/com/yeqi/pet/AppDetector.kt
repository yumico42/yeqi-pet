package com.yeqi.pet

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.util.*

class AppDetector(
    private val context: Context,
    private val webView: WebView?,
    private val sync: SupabaseSync?
) {

    private var timer: Timer? = null
    private var lastApp: String = ""
    private var isAway = false
    private var awayStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val launcherPackages: Set<String> by lazy { loadLauncherPackages() }

    fun start() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                val current = getForegroundApp()
                if (current.isNotEmpty() && current != lastApp) {
                    lastApp = current
                    onAppChanged(current)
                }
                if (current.isNotEmpty()) {
                    handlePresence(current)
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

    private fun handlePresence(packageName: String) {
        val now = System.currentTimeMillis()
        if (!isPetOrLauncher(packageName)) {
            if (!isAway) {
                isAway = true
                awayStartedAt = now
            }
            return
        }

        if (!isAway) return

        val awayDuration = now - awayStartedAt
        isAway = false
        awayStartedAt = 0L
        if (awayDuration >= RETURN_SHORT_THRESHOLD_MS) {
            notifyReturned(awayDuration)
        }
    }

    private fun isPetOrLauncher(packageName: String): Boolean {
        return packageName == context.packageName || launcherPackages.contains(packageName)
    }

    private fun notifyReturned(awayDuration: Long) {
        mainHandler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onAppReturn && window.petEngine.onAppReturn($awayDuration)",
                null
            )
        }
    }

    private fun loadLauncherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    fun stop() {
        timer?.cancel()
        timer = null
    }

    companion object {
        private const val RETURN_SHORT_THRESHOLD_MS = 30_000L
    }
}
