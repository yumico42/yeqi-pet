package com.yeqi.pet

import android.os.Handler
import android.os.Looper

object PetEventBus {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var notificationListener: (() -> Unit)? = null

    fun setNotificationListener(listener: (() -> Unit)?) {
        notificationListener = listener
    }

    fun notifyNotificationPosted() {
        mainHandler.post {
            notificationListener?.invoke()
        }
    }
}
