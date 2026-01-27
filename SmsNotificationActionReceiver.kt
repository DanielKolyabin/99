package org.maksec.messengersDynamicFeature.data

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maksec.MainActivity
import org.maksec.navigation.components.profile.copyToClipboard
import org.maksec.shared.AppLogger
import org.maksec.shared.di.LocalDaoHolder.messengersDao

class SmsNotificationActionReceiver : BroadcastReceiver() {
    private val TAG = "org.maksec.sms.action.receiver"

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra("ACTION_ID") ?: return
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER")
        val messageId = intent.getLongExtra("MESSAGE_ID", -1)
        val userId = intent.getLongExtra("USER_ID", -1)
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

        AppLogger.d(TAG, "Action received: $actionId for number: $phoneNumber")

        when (actionId) {
            "view_sms" -> handleViewSms(context, phoneNumber, notificationId)
        }
    }

    private fun handleViewSms(context: Context, phoneNumber: String?, notificationId: Int) {
        AppLogger.d(TAG, "🔄 Пробуем открыть мессенджер для: $phoneNumber")

        try {
            if (phoneNumber != null) {
                // СПОСОБ 1: Прямой запуск приложения по пакету
                val smsPackages = listOf(
                    "com.android.mms",                    // Стандартное Android
                    "com.google.android.apps.messaging",  // Google Messages
                    "com.samsung.android.messaging",      // Samsung
                    "com.xiaomi.mms",                     // Xiaomi
                    "com.huawei.messaging",               // Huawei
                    "com.oneplus.mms"                     // OnePlus
                )

                for (pkg in smsPackages) {
                    try {
                        AppLogger.d(TAG, "🔄 Пробуем пакет: $pkg")
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(launchIntent)
                            AppLogger.d(TAG, "✅ Запущено приложение: $pkg")

                            // Дополнительно показываем Toast
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                android.widget.Toast.makeText(
                                    context,
                                    "📱 Открыто приложение сообщений",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }

                            cancelNotification(context, notificationId)
                            return
                        }
                    } catch (e: Exception) {
                        AppLogger.d(TAG, "❌ Пакет $pkg не найден: ${e.message}")
                    }
                }

                // СПОСОБ 2: Если не нашли приложение, показываем выбор
                AppLogger.d(TAG, "🔄 Показываем выбор приложения")
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("smsto:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }

                // Создаем Intent chooser
                val chooser = Intent.createChooser(smsIntent, "Выберите приложение для SMS")
                chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(chooser)
                AppLogger.d(TAG, "✅ Показан выбор приложения")

            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "💥 ОШИБКА открытия: ${e.message}")

            // Если всё не сработало, просто копируем номер
            copyToClipboard(context.toString(), phoneNumber ?: "")

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "📱 Номер $phoneNumber скопирован\nОткройте приложение сообщений вручную",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        cancelNotification(context, notificationId)
    }



    private fun cancelNotification(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
                AppLogger.d(TAG, "Notification $notificationId cancelled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Cannot cancel notification: ${e.message}")
            }
        }
    }
}