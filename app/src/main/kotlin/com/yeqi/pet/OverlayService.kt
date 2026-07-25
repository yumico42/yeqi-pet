package com.yeqi.pet

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var gestureHandler: GestureHandler? = null
    private var supabaseSync: SupabaseSync? = null
    private var appDetector: AppDetector? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var lastChargingState: Boolean? = null
    private var lastBatteryLevel: Int? = null
    private var lowBatteryNotified = false

    companion object {
        const val CHANNEL_ID = "yeqi_pet_channel"
        const val NOTIFICATION_ID = 1
        private const val LOW_BATTERY_THRESHOLD = 20
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("夜栖趴在屏幕上"))
        setupOverlay()
        registerPowerReceiver()
        PetEventBus.setNotificationListener {
            evaluatePetJavascript(
                "window.petEngine && window.petEngine.onNotification && window.petEngine.onNotification()"
            )
        }
        supabaseSync?.startPolling()
        appDetector = AppDetector(this, overlayView, supabaseSync)
        appDetector?.start()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val width = dpToPx(120)
        val height = dpToPx(156)
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    replayPowerState()
                }
            }
            loadUrl("file:///android_asset/pet.html")
        }

        supabaseSync = SupabaseSync(overlayView)
        gestureHandler = GestureHandler(windowManager!!, overlayView!!, params, supabaseSync)
        overlayView?.setOnTouchListener(gestureHandler)

        windowManager?.addView(overlayView, params)
    }

    private fun registerPowerReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> handleCharging(true)
                    Intent.ACTION_POWER_DISCONNECTED -> handleCharging(false)
                    Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
                }
            }
        }
        powerReceiver = receiver

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(receiver, filter)
        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            receiver.onReceive(this, it)
        }
    }

    private fun handleBatteryChanged(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        handleCharging(charging)

        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (rawLevel < 0 || scale <= 0) return

        val level = rawLevel * 100 / scale
        lastBatteryLevel = level
        if (level < LOW_BATTERY_THRESHOLD && !lowBatteryNotified) {
            lowBatteryNotified = true
            evaluatePetJavascript(
                "window.petEngine && window.petEngine.onBatteryLow && window.petEngine.onBatteryLow($level)"
            )
        } else if (level >= LOW_BATTERY_THRESHOLD) {
            lowBatteryNotified = false
        }
    }

    private fun handleCharging(charging: Boolean) {
        if (lastChargingState == charging) return
        lastChargingState = charging
        evaluatePetJavascript(
            "window.petEngine && window.petEngine.onCharging && window.petEngine.onCharging($charging)"
        )
    }

    private fun replayPowerState() {
        lastChargingState?.let { charging ->
            evaluatePetJavascript(
                "window.petEngine && window.petEngine.onCharging && window.petEngine.onCharging($charging)"
            )
        }
        lastBatteryLevel?.takeIf { it < LOW_BATTERY_THRESHOLD }?.let { level ->
            evaluatePetJavascript(
                "window.petEngine && window.petEngine.onBatteryLow && window.petEngine.onBatteryLow($level)"
            )
        }
    }

    private fun evaluatePetJavascript(script: String) {
        overlayView?.post {
            overlayView?.evaluateJavascript(script, null)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83D\uDC3E 夜栖")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "夜栖桌宠", NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        supabaseSync?.stopPolling()
        appDetector?.stop()
        PetEventBus.setNotificationListener(null)
        powerReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        powerReceiver = null
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
