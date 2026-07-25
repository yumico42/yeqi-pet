package com.yeqi.pet

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlin.random.Random

class PetNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName == packageName) return
        if (Random.nextFloat() < NOTIFICATION_REACT_CHANCE) {
            PetEventBus.notifyNotificationPosted()
        }
    }

    companion object {
        private const val NOTIFICATION_REACT_CHANCE = 0.3f
    }
}
