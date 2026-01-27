package org.maksec.shared.data.db.messengers

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.maksec.shared.AppLogger
import org.maksec.shared.core.daysAgoUnix
import org.maksec.shared.core.hoursAgoUnix
import org.maksec.shared.screens.util.LabelType
import org.maksec.shared.screens.util.MessengerSource
import org.maksec.shared.screens.util.toDangerLevel
import kotlin.time.ExperimentalTime

@Dao
interface MessengersDao {
    val TAG: String
        get() = "org.maksec.messengersDao"

    @Query("SELECT * FROM MessengerUser")
    fun getUsers(): Flow<List<MessengerUser>>

    @Query("SELECT * FROM MessengerUser WHERE isIgnored = 1")
    fun getIgnoredUsers(): Flow<List<MessengerUser>>

    @Query("SELECT * FROM MessengerUser WHERE isBlocked = 1")
    fun getBlockedUsers(): Flow<List<MessengerUser>>

    @Query("SELECT * FROM message ORDER BY date DESC")
    fun getMessagesByDateDesc(): Flow<List<Message>>

    @Query("SELECT * FROM message ORDER BY date ASC")
    fun getMessagesByDateAsc(): Flow<List<Message>>

    @Query("SELECT * FROM message ORDER BY dangerLevel DESC")
    fun getMessagesByDangerDesc(): Flow<List<Message>>

    @Query("SELECT * FROM message ORDER BY dangerLevel ASC")
    fun getMessagesByDangerAsc(): Flow<List<Message>>

    @Query("SELECT date FROM message ORDER BY date DESC LIMIT 1")
    fun getLastMessageDate(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM MessageStats WHERE date >= :fromDate AND dangerLevel = :dangerLevel")
    fun getMessagesStatsByDangerLevelSince(fromDate: Int, dangerLevel: DangerLevel): Flow<Int>

    @Query("SELECT COUNT(*) FROM MessageStats WHERE dangerLevel = :dangerLevel")
    fun getMessagesStatsByDangerLevel(dangerLevel: DangerLevel): Flow<Int>

    @Query("DELETE FROM message WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query(
        """
    SELECT
        CASE :tag
            WHEN 'SPAM' THEN spamTags
            WHEN 'SCAM' THEN scamTags
            WHEN 'SAFE' THEN safeTags
            WHEN 'AD' THEN adTags
            ELSE 0
        END
    FROM MessengerUser
    WHERE userId = :userId
    """
    )
    fun getTagCountByUserAndTag(userId: Long, tag: Tag): Flow<Int>


    @Query("SELECT COUNT(*) FROM MessengerUser WHERE isBlocked = 1")
    fun getBlockedChatsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Message WHERE labels LIKE '%' || :labelType || '%'")
    fun getLabelTypeCount(labelType: LabelType): Flow<Int>

    @Query("UPDATE MessengerUser SET tag = :newTag WHERE userId = :userId")
    suspend fun updateUserTag(userId: Long, newTag: Tag?)

    @Query("UPDATE MessengerUser SET spamTags = :spamTags, scamTags = :scamTags, safeTags = :safeTags, adTags = :adTags WHERE userId = :userId")
    suspend fun updateUserTags(userId: Long, spamTags: Int, scamTags: Int, safeTags: Int, adTags: Int)

    @Query("UPDATE message SET voiceNoteTranscript = :transcription, voiceNoteProcessed = 1 WHERE remoteVoiceNoteId = :voiceNoteId")
    suspend fun updateMessageVoiceNoteTranscription(voiceNoteId: String, transcription: String)

    @Query("SELECT * FROM Message WHERE remoteVoiceNoteId = :voiceNoteId LIMIT 1")
    suspend fun getMessageByVoiceNoteId(voiceNoteId: String): Message?

    @Query("UPDATE message SET localDocumentPath = :localPath, documentSize = :size, documentDownloaded = 1 WHERE remoteDocumentId = :documentId")
    suspend fun updateDownloadedDocumentData(documentId: String, localPath: String, size: Long)

    @Query("UPDATE message SET localPhotoPath = :localPath, photoSize = :size, photoDownloaded = 1  WHERE remotePhotoId = :photoId")
    suspend fun updateDownloadedPhotoData(photoId: String, localPath: String, size: Long)

    @Upsert
    suspend fun upsertMessage(message: Message)

    @Transaction
    suspend fun upsertMessageWithCheck(message: Message) {
        val user = getMessengerUser(message.senderUserId).first()
        if (user == null) {
            AppLogger.e(TAG, "User must exist for message $message")
            return
        }
        val chat = getChat(message.chatId).first()
        if (chat == null) {
            AppLogger.e(TAG, "Chat must exist for message $message")
            return
        }
        upsertMessage(message)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: Message)

    @Query(
        """
    SELECT * FROM Message 
    WHERE urls IS NOT NULL 
      AND urlsProcessed = 0
      """
    )
    fun getMessagesWithUnprocessedUrls(): Flow<List<Message>>

    @Query(
        """
    SELECT * FROM Message 
    WHERE remotePhotoId IS NOT NULL 
      AND photoProcessed = 0
      AND photoDownloaded = 1
      """
    )
    fun getMessagesWithUnprocessedPhotos(): Flow<List<Message>>

    @Query(
        """
    SELECT * FROM Message 
    WHERE remoteDocumentId IS NOT NULL 
      AND documentProcessed = 0
      AND documentDownloaded = 1
      """
    )
    fun getMessagesWithUnprocessedDocuments(): Flow<List<Message>>

    @Query(
        """
    SELECT * FROM Message 
    WHERE textProcessed = 0
      """
    )
    fun getMessagesWithUnprocessedText(): Flow<List<Message>>

    @Upsert
    suspend fun upsertChat(chat: Chat)

    @Query("SELECT * FROM Chat WHERE chatId = :chatId")
    fun getChat(chatId: Long): Flow<Chat?>

    @Query("SELECT * FROM Message WHERE id = :messageId")
    fun getMessage(messageId: Long): Flow<Message?>

    @Upsert
    suspend fun upsertMessengerUser(user: MessengerUser)

    @Query("SELECT * FROM MessengerUser WHERE userId = :userId")
    fun getMessengerUser(userId: Long): Flow<MessengerUser?>

    @Query("UPDATE MessengerUser SET isBlocked = :newVal WHERE userId = :userId")
    suspend fun updateUserBlocked(userId: Long, newVal: Boolean): Int

    @Query("UPDATE Message SET messageAction = :newAction WHERE id = :messageId")
    suspend fun updateMessageAction(messageId: Long, newAction: MessageAction?): Int

    @Query("UPDATE Message SET messageAction = :newAction WHERE id = :messageId")
    suspend fun updateMessageDeleted(messageId: Long, newAction: MessageAction? = MessageAction.DELETE): Int

    @Query("UPDATE MessengerUser SET isIgnored = :newVal WHERE userId = :userId")
    suspend fun updateUserIgnored(userId: Long, newVal: Boolean): Int

    @Query("UPDATE Message SET messageAction = :messageAction WHERE chatId = :chatId")
    suspend fun markMessagesDeletedByChatId(chatId: Long, messageAction: MessageAction = MessageAction.DELETE): Int

    @Query("UPDATE Message SET messageAction = :messageAction WHERE id IN (:messagesId)")
    suspend fun markMessagesDeletedByMessagesId(
        messagesId: List<Long>,
        messageAction: MessageAction = MessageAction.DELETE
    ): Int

    @Query("UPDATE Message SET dangerLevel = :newDangerLevel WHERE id = :messageId")
    suspend fun updateDangerLevelByMessageId(messageId: Long, newDangerLevel: DangerLevel)

    @Transaction
    suspend fun updateDangerLevelManually(messageId: Long, newDangerLevel: DangerLevel) {
        updateDangerLevelByMessageId(messageId, newDangerLevel)

        val msg = getMessage(messageId).first() ?: return
        upsertMessageStats(
            listOf(
                MessageStats(
                    id = msg.id,
                    date = msg.date,
                    dangerLevel = newDangerLevel
                )
            )
        )
    }

    @Query("DELETE FROM MessengerUser WHERE userId = :userId")
    suspend fun deleteMessengerUser(userId: Long)

    @Query("DELETE FROM Message WHERE source = :source")
    suspend fun deleteMessagesBySource(source: MessengerSource)

    @Query("DELETE FROM MessengerUser WHERE source = :source")
    suspend fun deleteUsersBySource(source: MessengerSource)

    @Query("DELETE FROM Chat WHERE source = :source")
    suspend fun deleteChatsBySource(source: MessengerSource)

    @Transaction
    suspend fun deleteAllBySource(source: MessengerSource) {
        deleteMessagesBySource(source)
        deleteUsersBySource(source)
        deleteChatsBySource(source)
    }

    @Query("SELECT * FROM message WHERE senderUserId = :senderId")
    fun getMessagesBySenderId(senderId: Long): Flow<List<Message>>

    @Transaction
    suspend fun addMessageLabelsByUserId(userId: Long, newLabels: Set<LabelType>) {
        val messages = getMessagesBySenderId(userId).first()
        messages.forEach { message ->
            val combined = message.labels.toMutableSet().apply { addAll(newLabels) }
            val newDangerLevel = getMaxDanger(combined.map { it.toDangerLevel() })
            val updated = message.copy(
                labels = combined,
                dangerLevel = newDangerLevel,
            )
            upsertMessageWithCheck(updated)

            val stat =
                MessageStats(
                    id = message.id,
                    date = message.date,
                    dangerLevel = newDangerLevel
                )

            upsertMessageStat(stat)
        }
    }

    @Query("SELECT * FROM Message WHERE senderUserId = :userId")
    suspend fun getMessagesByUserId(userId: Long): List<Message>

    // Returns all labels as a flat Set
    suspend fun getUserLabelsAsSet(userId: Long): Set<LabelType> {
        return getMessagesByUserId(userId)
            .flatMap { it.labels } // flatten all sets of labels
            .toSet()
    }

    suspend fun getScanUserLabelsByUserId(userId: Long): Set<LabelType> {
        return getUserLabelsAsSet(userId).filter {
            it == LabelType.FRAUDULENT_ACCOUNT ||
                    it == LabelType.SUSPICIOUS_ACCOUNT ||
                    it == LabelType.SAFE_ACCOUNT
        }.toSet()
    }

    @Transaction
    suspend fun markProcessedForSender(
        senderId: Long,
        chatId: Long,
        messageId: Long,
        newLabels: Set<LabelType>, // These labels are added only to the message with id == messageId
        flag: ProcessFlag,
        since: Long = hoursAgoUnix(3),
    ) {
        // Get all messages from the same sender
        val messages = getMessagesFromUserSince(
            userId = senderId,
            chatId = chatId,
            since = since,
        )

        if (messages.isEmpty()) return

        // Merge all existing labels (from triggers) from all messages with the new ones
        val combinedLabels = messages
            .flatMap { it.labels }
            .filter {
                it == LabelType.SUSPICIOUS_CHAT ||
                it == LabelType.FRAUDULENT_CHAT
            }
            .toMutableSet()
            .apply { addAll(newLabels) }
            .toList()

        val maxDanger = getMaxDanger(combinedLabels.map { it.toDangerLevel() })

        // This label is added to all messages from the user with id == senderId
        // Label is added only for primary/secondary/mixed triggers
        val labelToAdd = if (flag == ProcessFlag.TEXT &&
            newLabels.any {it.toDangerLevel() != DangerLevel.SAFE })
            when (maxDanger) {
                DangerLevel.SAFE -> null
                DangerLevel.SUSPICIOUS -> LabelType.SUSPICIOUS_CHAT
                DangerLevel.CRITICAL -> LabelType.FRAUDULENT_CHAT
            } else null

        val updatedMessages = messages.map { msg ->
            val addedLabels = listOfNotNull(labelToAdd) + if (msg.id == messageId) newLabels else emptySet()
            msg.copy(
                labels = msg.labels + addedLabels,
                dangerLevel = getMaxDanger((msg.labels + addedLabels).map { it.toDangerLevel() }),
                urlsProcessed = if (msg.id == messageId && flag == ProcessFlag.URL) true else msg.urlsProcessed,
                textProcessed = if (msg.id == messageId && flag == ProcessFlag.TEXT) true else msg.textProcessed,
                documentProcessed = if (msg.id == messageId && flag == ProcessFlag.DOCUMENT) true else msg.documentProcessed,
                photoProcessed = if (msg.id == messageId && flag == ProcessFlag.PHOTO) true else msg.photoProcessed,
            )
        }
        upsertMessages(updatedMessages)

        val stats = updatedMessages.map { msg ->
            MessageStats(
                id = msg.id,
                date = msg.date,
                dangerLevel = msg.dangerLevel
            )
        }

        upsertMessageStats(stats)
    }

    @Upsert
    suspend fun upsertMessages(messages: List<Message>)

    @Upsert
    suspend fun upsertMessageStats(messageStats: List<MessageStats>)

    @Upsert
    suspend fun upsertMessageStat(messageStat: MessageStats)

    @Transaction
    suspend fun dropAll() {
        deleteAllMessages()
        deleteAllUsers()
        deleteAllChats()
        deleteAllMessageStats()
    }

    @Query("DELETE FROM Message")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM MessengerUser")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM Chat")
    suspend fun deleteAllChats()

    @Query("DELETE FROM MessageStats")
    suspend fun deleteAllMessageStats()

    @OptIn(ExperimentalTime::class)
    @Transaction
    suspend fun cleanupOldMessages(days: Int = 7) {
        val threshold = daysAgoUnix(days)

        deleteMessagesOlderThan(threshold)
        deleteUnusedUsers()
        deleteUnusedChats()
    }

    @Query("DELETE FROM Message WHERE createdAt < :threshold")
    suspend fun deleteMessagesOlderThan(threshold: Long)

    @Query("DELETE FROM MessengerUser WHERE userId NOT IN (SELECT DISTINCT senderUserId FROM Message)")
    suspend fun deleteUnusedUsers()

    @Query("DELETE FROM Chat WHERE chatId NOT IN (SELECT DISTINCT chatId FROM Message)")
    suspend fun deleteUnusedChats()

    @Query(
        """
    SELECT * FROM Message 
    WHERE 
        ((urls IS NULL OR urlsProcessed = 1) AND
         (text IS NULL OR textProcessed = 1) AND
         (remotePhotoId IS NULL OR photoProcessed = 1) AND
         (remoteDocumentId IS NULL OR documentProcessed = 1)) AND 
        source = :source AND notifiedUser = 0
        """
    ) // Returns processed messages, which the user should be notified about
    fun getProcessedMessagesNotNotified(source: MessengerSource): Flow<List<Message>>

    @Query(
        """
    SELECT * FROM Message 
    WHERE 
        ((urls IS NULL OR urlsProcessed = 1) AND
         (text IS NULL OR textProcessed = 1) AND
         (remotePhotoId IS NULL OR photoProcessed = 1) AND
         (remoteDocumentId IS NULL OR documentProcessed = 1)) AND 
        relativeNotificationChecked = 0
"""
    ) // Returns processed messages, which the relative should be notified about
    fun getProcessedMessagesForRelativeNotification(): Flow<List<Message>>

    @Query("UPDATE Message SET notifiedUser = 1 WHERE id = :messageId")
    suspend fun markMessageNotifiedUser(messageId: Long)

    @Query("UPDATE Message SET notifiedRelative = 1, relativeNotificationChecked = 1 WHERE id = :messageId")
    suspend fun markMessageNotifiedRelative(messageId: Long)

    @Query("UPDATE Message SET relativeNotificationChecked = 1 WHERE id = :messageId")
    suspend fun markMessageRelativeNotificationChecked(messageId: Long)

    @Query(
        """
        SELECT * FROM Message
        WHERE senderUserId = :userId AND createdAt >= :since AND chatId = :chatId
"""
    )
    suspend fun getMessagesFromUserSince(
        userId: Long,
        chatId: Long,
        since: Long,
    ): List<Message>
}