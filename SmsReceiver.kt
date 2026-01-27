package org.maksec.messengersDynamicFeature.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.content.ContextCompat
import org.maksec.shared.AppLogger
import org.maksec.shared.features.messages.domain.SmsMessageData
import org.maksec.messengerscommonfeature.di.MessengersFeatureDependencies.smsRepository
import androidx.core.net.toUri
import org.maksec.shared.core.ServiceManager
import org.maksec.shared.data.db.incidents.Module
import org.maksec.messengerscommonfeature.di.MessengersFeatureDependencies
import java.security.MessageDigest
import kotlin.math.absoluteValue

class SmsReceiver : BroadcastReceiver() {
    @get:SuppressLint("NewApi")
    private val isMessagesServiceRunning: Boolean
        get() = ServiceManager.realServicesState.value[Module.MESSAGES] == true
    data class DangerousSmsData(
        val sender: String,
        val text: String,
        val timestamp: Long,
        var notificationShown: Boolean = false
    )

    companion object {
        private const val TAG = "org.maksec.sms.receiver"
        private val messageQueue = mutableListOf<SmsMessageData>()

        // Очередь опасных SMS для NotificationListener
        val dangerousSmsQueue = mutableListOf<DangerousSmsData>()

        fun tryFlushQueue() {
            if (!MessengersFeatureDependencies.isInitialized()) {
                AppLogger.d(TAG, "Trying to flush queue, but repositories are not initialized ")
                return
            }
            val repo = smsRepository

            if (!repo.serviceEnabled.value) {
                AppLogger.d(TAG, "Deferring flush: serviceEnabled=${repo.serviceEnabled.value}")
                return
            }

            for (message in messageQueue) {
                repo.onNewMessage(
                    message.id,
                    message.sender,
                    message.text,
                    message.timestamp,
                    message.threadId,
                    message.protocol,
                    message.serviceCenter
                )
            }
            messageQueue.clear()
        }

        // Проверяем, есть ли опасное SMS в очереди
        fun findMatchingDangerousSms(sender: String, text: String): DangerousSmsData? {
            return dangerousSmsQueue.firstOrNull { sms ->
                sms.sender == sender &&
                        (sms.text.contains(text.take(20)) || text.contains(sms.text.take(20)))
            }
        }
    }

    init {
        AppLogger.i(TAG, "SmsReceiver initialized")
    }

    override fun onReceive(context: Context, intent: Intent) {
        // ⚠️ УБИРАЕМ ВРЕМЕННОЕ ОТКЛЮЧЕНИЕ - ВКЛЮЧАЕМ ОБРАТНО ⚠️
        // AppLogger.d(TAG, "🚫🚫🚫 SmsReceiver TEMPORARILY DISABLED - Testing NotificationListener 🚫🚫🚫")
        // return  // УБИРАЕМ ЭТУ СТРОЧКУ!

        if (!hasSmsPermissions(context)) {
            AppLogger.e(TAG, "SmsReceiver: Missing SMS permissions, ignoring broadcast")
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            AppLogger.d(TAG, "SMS received but READ_SMS permission is not granted")
            return
        }

        if (intent.action != "android.provider.Telephony.SMS_RECEIVED" || !isMessagesServiceRunning) {
            AppLogger.d(TAG, "Not SMS_RECEIVED or service not running")
            return
        }

        AppLogger.d(TAG, "✅ SmsReceiver: Processing SMS")

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        for (smsMessage in messages) {
            val sender = smsMessage.originatingAddress ?: "Unknown"
            val text = smsMessage.messageBody ?: ""

            AppLogger.d(TAG, "SMS от $sender: ${text.take(50)}...")

            // Проверяем, опасное ли SMS
            val isDangerous = checkIfSmsIsDangerous(text)

            if (isDangerous) {
                AppLogger.d(TAG, "🚨 Опасное SMS обнаружено!")

                // Добавляем в очередь опасных SMS для NotificationListener
                val dangerousData = DangerousSmsData(
                    sender = sender,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                dangerousSmsQueue.add(dangerousData)

                // Очищаем старые записи (старше 30 секунд)
                dangerousSmsQueue.removeAll {
                    System.currentTimeMillis() - it.timestamp > 30000
                }

                AppLogger.d(TAG, "Добавлено в опасную очередь, всего: ${dangerousSmsQueue.size}")

                // НЕ обрабатываем дальше - пусть NotificationListener покажет уведомление
                continue
            }

            // Для неопасных SMS - старый код
            val messageData = SmsMessageData(
                id = generateMessageId(sender, smsMessage.timestampMillis, text),
                sender = sender,
                text = text,
                timestamp = (smsMessage.timestampMillis / 1000L).toInt(),
                threadId = getThreadId(context, sender),
                protocol = smsMessage.protocolIdentifier,
                serviceCenter = smsMessage.serviceCenterAddress,
                isMms = false,
                readStatus = 0,
                subject = null,
                status = smsMessage.status,
                creator = smsMessage.emailFrom,
                emailBody = smsMessage.emailBody,
                pseudoSubject = smsMessage.pseudoSubject
            )

            if (!MessengersFeatureDependencies.isInitialized()) {
                messageQueue.add(messageData)
                return
            }

            val repo = smsRepository

            if (repo.serviceEnabled.value) {
                repo.onNewMessage(
                    messageData.id,
                    messageData.sender,
                    messageData.text,
                    messageData.timestamp,
                    messageData.threadId,
                    messageData.protocol,
                    messageData.serviceCenter
                )
            } else {
                messageQueue.add(messageData)
            }
        }
    }

    private fun checkIfSmsIsDangerous(text: String): Boolean {
        val dangerousKeywords = listOf(
            "деньги", "переведи", "срочно", "код", "пароль",
            "банк", "карта", "перевод", "займ", "кредит",
            "оплати", "плати", "купи", "счет", "платеж",
            "парол", "аккаунт", "номер", "билет", "выиграл"
        )

        val lowerText = text.lowercase()
        return dangerousKeywords.any { keyword -> lowerText.contains(keyword) }
    }

    private fun hasSmsPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getThreadId(context: Context, address: String?): Long {
        if (address == null) return -1

        val uri = "content://mms-sms/conversations".toUri()
        val projection = arrayOf("_id")
        val selection = "address = ?"
        val selectionArgs = arrayOf(address)

        context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return -1
    }

    private fun generateMessageId(
        sender: String?,
        timestamp: Long,
        text: String?
    ): Long {
        val input = "${sender}|$timestamp|${text ?: ""}"
        val bytes = input.toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
        return hash.take(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }.absoluteValue
    }
}