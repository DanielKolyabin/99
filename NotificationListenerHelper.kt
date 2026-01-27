package org.maksec.navigation.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import org.maksec.shared.AppLogger

object NotificationListenerHelper {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return try {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""

            val packageName = context.packageName
            val listenerName = "$packageName/org.maksec.messengersDynamicFeature.data.NotificationListener"

            val isEnabled = enabledListeners.contains(listenerName)

            AppLogger.d("NotificationHelper", "Статус: $isEnabled")
            AppLogger.d("NotificationHelper", "Listener: $listenerName")

            isEnabled
        } catch (e: Exception) {
            AppLogger.e("NotificationHelper", "Ошибка проверки: ${e.message}")
            false
        }
    }

    fun requestNotificationListenerPermission(context: Context) {
        if (!isNotificationListenerEnabled(context)) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Toast.makeText(
                context,
                "Включите MaKSec в списке служб уведомлений",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}