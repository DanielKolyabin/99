package org.maksec.messengersDynamicFeature.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.maksec.shared.AppLogger
import org.maksec.shared.data.db.messengers.Message
import org.maksec.shared.data.db.messengers.MessengerUser
import org.maksec.shared.screens.util.MessengerSource

class DangerousSmsNotifier {
    companion object {
        fun initChannel(context: Context) {
            createNotificationChannel(context)
            AppLogger.d(TAG, "Dangerous SMS notification channel initialized")
        }
        private const val TAG = "DangerousSmsNotifier"
        private const val CHANNEL_ID = "dangerous_sms_channel"
        private const val CHANNEL_NAME = "Опасные SMS"
        private const val NOTIFICATION_ID_BASE = 1000

        fun showNotification(context: Context, message: Message, user: MessengerUser) {
            try {
                createNotificationChannel(context)

                val notificationId = NOTIFICATION_ID_BASE + message.id.hashCode()
                val notification = buildNotification(
                    context = context,
                    phoneNumber = user.phoneNumber ?: "Неизвестный номер",
                    messageText = message.text,
                    message = message,
                    user = user,
                    notificationId = notificationId
                )

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                notificationManager.notify(notificationId, notification)

                AppLogger.d(TAG, "Dangerous SMS notification shown for ${user.phoneNumber}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to show notification: ${e.message}", e)
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления об опасных SMS сообщениях"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500)
                    enableLights(true)
                    lightColor = 0xFFEA4C29.toInt()
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        private fun buildNotification(
            context: Context,
            phoneNumber: String,
            messageText: String?,
            message: Message,
            user: MessengerUser,
            notificationId: Int
        ): android.app.Notification {
            val resources = context.resources

            // Используем хардкодные строки для простоты
            val title = "⚠️ Опасное SMS от $phoneNumber"

            // Формируем текст сообщения безопасно
            val messagePreview = if (!messageText.isNullOrEmpty()) {
                "💬 Текст: ${messageText.take(200)}${if (messageText.length > 200) "..." else ""}\n\n"
            } else {
                "💬 Текст: [пустое сообщение]\n\n"
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(org.maksec.shared.R.drawable.maksec_logo)
                .setContentTitle(title)
                .setContentText("Обнаружено подозрительное сообщение")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("🚨 ОПАСНОЕ СООБЩЕНИЕ ОБНАРУЖЕНО!\n\n" +
                                "📱 Отправитель: $phoneNumber\n" +
                                messagePreview +
                                "Рекомендуем заблокировать этого отправителя")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setColor(0xFFEA4C29.toInt())
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOnlyAlertOnce(true)

            addActionButton(context, builder, message, user, phoneNumber, notificationId)

            return builder.build()
        }

        private fun addActionButton(
            context: Context,
            builder: NotificationCompat.Builder,
            message: Message,
            user: MessengerUser,
            phoneNumber: String,
            notificationId: Int
        ) {
            // Меняем на кнопку "Просмотр"
            val viewIntent = Intent(context, SmsNotificationActionReceiver::class.java).apply {
                action = "ACTION_VIEW_SMS"
                putExtra("ACTION_ID", "view_sms")
                putExtra("PHONE_NUMBER", phoneNumber)
                putExtra("MESSAGE_ID", message.id)
                putExtra("USER_ID", user.userId)
                putExtra("NOTIFICATION_ID", notificationId)
            }

            val viewPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 1,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "👁️ Просмотр",
                    viewPendingIntent
                ).build()
            )
        }
    }
}