package org.maksec.messengersDynamicFeature.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import org.maksec.shared.AppLogger
import maksec.shared.generated.resources.Res.string
import maksec.shared.generated.resources.*
import org.maksec.shared.data.network.util.Result
import org.maksec.shared.di.AppPreferences
import org.maksec.shared.di.LocalAppContextHolder
import org.maksec.shared.features.messages.domain.SmsRepository
import org.maksec.shared.screens.util.MessengerSource
import org.maksec.messengerscommonfeature.data.notifications.MessengerNotificationDispatcher
import org.maksec.messengersDynamicFeature.data.util.toLongHash
import org.maksec.shared.di.LocalDaoHolder.messengersDao
import androidx.core.net.toUri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.mp.KoinPlatform.getKoin
import org.maksec.shared.core.getSharedContacts
import org.maksec.shared.features.messages.domain.ContactInfo
import org.maksec.shared.features.messages.domain.SmsError
import org.maksec.shared.core.getUrls
import org.maksec.shared.data.db.messengers.*
import org.maksec.shared.domain.LicenseFeature
import org.maksec.shared.domain.LicenseManager
import org.maksec.shared.features.antivirus.domain.VirusScanner
import org.maksec.messengerscommonfeature.data.notifications.NotificationAction


object SmsRepositoryImpl : SmsRepository {
    private lateinit var appContext: Context
    private val licenseManager: LicenseManager = getKoin().get()
    private const val TAG = "org.maksec.sms.repository"

    private val telephonyManager: TelephonyManager by lazy {
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val virusScanner: VirusScanner = getKoin().get()

    private val _serviceEnabled = MutableStateFlow(false)
    override val serviceEnabled: StateFlow<Boolean> = _serviceEnabled

    private var isInitialized = false

    init {
        coroutineScope.launch {
            messengersDao.getProcessedMessagesNotNotified(
                source = MessengerSource.SMS
            )
                .distinctUntilChanged()
                .collect { messages ->
                    messages.forEach { message ->
                        val user = messengersDao.getMessengerUser(message.senderUserId).first()?: return@forEach
                        sendNotification(message, user)
                        messengersDao.markMessageNotifiedUser(message.id)
                    }
                }
        }
    }

    override fun initialize() {
        if (isInitialized) return
        isInitialized = true

        if (!::appContext.isInitialized) {
            appContext = LocalAppContextHolder.applicationContext
            initNotificationChannels()
        }

        coroutineScope.launch {
            AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.DEFEND_SMS)
                .distinctUntilChanged()
                .collect { enabled ->
                    AppLogger.d(TAG, "Sms protection setting changed: $enabled")
                    if (enabled) {
                        if (checkSmsPermissions()) {
                            resumeService()
                        } else {
                            AppLogger.e(TAG, "Cannot enable SMS protection: missing permissions")
                            _serviceEnabled.value = false
                        }
                    } else {
                        pauseService()
                    }
                }
        }
    }

    override fun resumeService() {
        _serviceEnabled.value = true
        SmsReceiver.tryFlushQueue()
    }

    override fun pauseService() {
        _serviceEnabled.value = false
    }

    override fun onNewMessage(
        messageId: Long,
        sender: String,
        text: String,
        timestamp: Int,
        threadId: Long,
        protocol: Int,
        serviceCenter: String?
    ) {
        if (!serviceEnabled.value) return
        AppLogger.d(TAG, "Sms from $sender: $text")

        val urls = text.getUrls()
        val sharedContacts = text.getSharedContacts()

        coroutineScope.launch {
            if (!checkSmsPermissions()) {
                return@launch
            }
            val upsertedUser = updateMessengerUser(sender = sender) ?: return@launch
            val shouldAnalyze = shouldAnalyzeUser(upsertedUser)
            if (!shouldAnalyze) return@launch

            val entity = Message(
                id = messageId,
                source = MessengerSource.SMS,
                senderUserId = upsertedUser.userId,
                chatId = upsertedUser.userId,
                isOutgoing = false,
                date = timestamp,
                isSavedMessage = false,
                botId = 0,
                businessBotId = 0,
                mediaAlbumId = 0,

                text = text,
                textProcessed = false,
                urls = urls,
                urlsProcessed = false,
                sharedContacts = sharedContacts,

                remotePhotoId = null,
                localPhotoPath = null,
                photoSize = null,

                remoteVoiceNoteId = null,
                voiceNoteTranscript = null,
                voiceNoteProcessed = false,

                remoteDocumentId = null,
                localDocumentPath = null,
                documentSize = null,
            )

            createChat(upsertedUser.userId)
            messengersDao.upsertMessageWithCheck(entity)

            virusScanner.scanUser(
                user = upsertedUser,
            )

            delay(1500)

            val updatedMessage = messengersDao.getMessage(messageId).first()

            if (updatedMessage != null) {
                delay(1500)
            }
        }
    }

    private fun initNotificationChannels() {
        try {
            // Канал создается автоматически в DangerousSmsNotifier.createNotificationChannel
            // при первом вызове showNotification
            // Или можно вызвать createNotificationChannel напрямую если нужно
            AppLogger.d(TAG, "SMS notification channels will be initialized on demand")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error initializing notification channels: ${e.message}", e)
        }
    }

    override suspend fun changeIgnoreUser(userId: Long, messageId: Long, doIgnore: Boolean): Result<Unit, SmsError> =
        withContext(Dispatchers.IO) {
            AppLogger.d(TAG, "Changing sms user $userId ignored to $doIgnore")
            try {
                messengersDao.updateUserIgnored(userId, doIgnore)
                messengersDao.updateMessageAction(
                    messageId = messageId,
                    newAction = if (doIgnore) MessageAction.IGNORED else null
                )
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(SmsError.ProviderError(e.message ?: "Database error"))
            }
        }

    private suspend fun sendNotification(message: Message, user: MessengerUser) {
        if (user.isIgnored) return

        var shouldNotify = false

        when (message.dangerLevel) {
            DangerLevel.CRITICAL -> {
                shouldNotify = AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_CRITICAL).first()
            }
            DangerLevel.SUSPICIOUS -> {
                shouldNotify = AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.NOTIFY_MESSENGERS_SUSPICIOUS).first()
            }
            null, DangerLevel.SAFE -> { }
        }

        if (!shouldNotify) return

        val userBitmap: Bitmap? = user.pfpImageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        MessengerNotificationDispatcher.dispatchNotification(
            appContext = appContext,
            title = getString(
                if (message.dangerLevel == DangerLevel.CRITICAL)
                    string.new_critical_message_from
                else
                    string.new_suspicious_message_from,
                user.generateReadableName()
            ),
            body = message.text ?: "[not a text message]",
            actions = listOfNotNull(
                NotificationAction(
                    title = getString(string.ignore),
                    actionId = "ignore"
                ),
                if (licenseManager.currentLicenseHasFeature(LicenseFeature.NOTIFY_RELATIVE))
                    NotificationAction(
                        title = getString(string.notify_relative),
                        actionId = "notify_relative"
                    ) else null,
            ),
            message = message,
            chatId = message.chatId,
            user = user,
            largeIcon = userBitmap,
        )
    }

    private suspend fun createChat(chatId: Long) {
        val existing = messengersDao.getChat(chatId).first()
        if (existing != null) return
        val chat = Chat(
            chatId = chatId,
            source = MessengerSource.SMS,
            chatType = ChatType.PRIVATE,
            oppositeUserId = getUserIdFromThreadId(chatId),
        )
        messengersDao.upsertChat(chat)
        return
    }

    private suspend fun updateMessengerUser(sender: String): MessengerUser? {
        val userId = sender.toLongHash()
        val existing = messengersDao.getMessengerUser(userId).first()

        val phoneNumber = sender.takeIf { it.isNotBlank() }
        val isContact = phoneNumber?.let { isNumberInContacts(it) } ?: false

        val contactInfo = phoneNumber?.let { getContactInfo(it) } ?: ContactInfo(null, null, null)

        val messengerUser = MessengerUser(
            userId = sender.toLongHash(),
            source = MessengerSource.SMS,
            userName = contactInfo.name,
            firstName = null,
            lastName = null,
            phoneNumber = phoneNumber,
            pfpImageId = contactInfo.photoId,
            pfpImageBytes = contactInfo.photoBytes,
            isContact = isContact,
            isIgnored = existing?.isIgnored ?: false,
            isBlocked = existing?.isBlocked ?: false,
            tag = existing?.tag,
            spamTags = existing?.spamTags?: 0,
            scamTags = existing?.scamTags?: 0,
            safeTags = existing?.safeTags?: 0,
            adTags = existing?.adTags?: 0,
        )
        messengersDao.upsertMessengerUser(messengerUser)
        AppLogger.d(TAG, "Inserted user: ${messengerUser.generateReadableName()}")
        return messengersDao.getMessengerUser(userId).first()
    }

    private suspend fun shouldAnalyzeUser(user: MessengerUser): Boolean {
        if (user.isIgnored) return false

        val shouldAnalyze = when (user.isContact) {
            true -> {
                AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.ANALYZE_CONTACTS).first()
            }
            false -> {
                AppPreferences.getBooleanSettingFlow(AppPreferences.BooleanSetting.ANALYZE_STRANGERS).first()
            }
        }
        return shouldAnalyze
    }

    private fun getContactInfo(phoneNumber: String): ContactInfo? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        appContext.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getString(0)
                val photoUri = cursor.getString(1)

                val photoBytes = photoUri?.let {
                    appContext.contentResolver.openInputStream(it.toUri())?.use { stream ->
                        stream.readBytes()
                    }
                }

                return ContactInfo(
                    name = displayName,
                    photoBytes = photoBytes,
                    photoId = photoBytes?.toLongHash()
                )
            }
        }
        return null
    }

    private fun isNumberInContacts(phoneNumber: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        LocalAppContextHolder.applicationContext.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun getUserIdFromThreadId(threadId: Long): Long {
        val uri = "content://mms-sms/conversations/$threadId".toUri()
        val projection = arrayOf("address")

        LocalAppContextHolder.applicationContext.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val address = cursor.getString(0)
                return address.toLongHash()
            }
        }
        return threadId
    }

    private suspend fun checkSmsPermissions(): Boolean {
        try {
            val readSms = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

            val receiveSms = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED

            val readContacts = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            val hasPermissions = readSms && receiveSms && readContacts

            return hasPermissions

        } catch (e: Exception) {
            return false
        }
    }
}