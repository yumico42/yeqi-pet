package com.yeqi.pet

import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import kotlin.math.abs
import kotlin.math.sqrt

class GestureHandler(
    private val windowManager: WindowManager,
    private val overlayView: WebView,
    private val params: WindowManager.LayoutParams,
    private val sync: SupabaseSync?
) : View.OnTouchListener {

    companion object {
        const val DOUBLE_TAP_TIMEOUT = 300L
        const val LONG_PRESS_TIMEOUT = 600L
        const val MOVE_THRESHOLD = 10
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0
    private var tapCountResetHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                touchStartTime = System.currentTimeMillis()
                hasMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()
                if (abs(dx) > MOVE_THRESHOLD || abs(dy) > MOVE_THRESHOLD) {
                    hasMoved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(overlayView, params)
                }
            }
            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchStartTime
                if (!hasMoved) {
                    when {
                        elapsed > LONG_PRESS_TIMEOUT -> onLongPress()
                        System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> onDoubleTap()
                        else -> {
                            lastTapTime = System.currentTimeMillis()
                            onTap()
                        }
                    }
                } else {
                    val dx = (event.rawX - initialTouchX).toDouble()
                    val dy = (event.rawY - initialTouchY).toDouble()
                    val velocity = sqrt(dx * dx + dy * dy)
                    if (velocity > 200 && elapsed < 400) {
                        onFling()
                    }
                }
            }
        }
        return true
    }

    private fun onTap() {
        tapCount++
        tapCountResetHandler.removeCallbacksAndMessages(null)
        tapCountResetHandler.postDelayed({ tapCount = 0 }, 2000)

        if (tapCount >= 2) {
            callJs("onComboTap", tapCount.toString())
        } else {
            callJs("onTap", "")
        }
        sync?.reportGesture("tap")
    }

    private fun onDoubleTap() {
        callJs("onDoubleTap", "")
        sync?.reportGesture("double_tap")
    }

    private fun onLongPress() {
        callJs("onLongPress", "")
        sync?.reportGesture("long_press")
    }

    private fun onFling() {
        callJs("onFling", "")
        sync?.reportGesture("fling")
    }

    private fun callJs(method: String, arg: String) {
        overlayView.post {
            val call = if (arg.isBlank()) {
                "window.petEngine && window.petEngine.$method && window.petEngine.$method()"
            } else {
                "window.petEngine && window.petEngine.$method && window.petEngine.$method($arg)"
            }
            overlayView.evaluateJavascript(
                call, null
            )
        }
    }
}
