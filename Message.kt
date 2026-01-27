package org.maksec.shared.data.db.messengers

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import org.maksec.shared.core.unixSecondsNow
import org.maksec.shared.screens.util.MessengerSource
import org.maksec.shared.screens.util.LabelType
import kotlin.time.ExperimentalTime


@Entity (
    foreignKeys = [
        ForeignKey(
            entity = MessengerUser::class,
            parentColumns = ["userId"],
            childColumns = ["senderUserId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["senderUserId"]), Index(value = ["chatId"])]
)

data class Message @OptIn(ExperimentalTime::class) constructor(
    @PrimaryKey val id: Long,
    val source: MessengerSource,
    val senderUserId: Long, //We assume senderId = MessageSenderUser (Also can be messageSenderChat)
    val chatId: Long,
    val isOutgoing: Boolean,
    val date: Int, //Unix timestamp
    val isSavedMessage: Boolean, // true when savedMessagesTopicId != 0,
    val botId: Long  = 0, // if viaBotUserId = 0, not a bot
    val businessBotId: Long = 0, // if senderBusinessBotUserId = 0, not a business bot
    val mediaAlbumId: Long = 0, // Only audios, documents, photos and videos can be grouped together in albums, 0 if none

    // Here Message.content splits into different categories, we process only few of them, more: https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_message_content.html
    // MessageText:
    val text: String? = null, //Under content.text.text  OR  Message.content.caption.text.text (Non-MessageText have this)
    val textProcessed:  Boolean = false,
    val urls: List<Pair<Int, Int>>? = null, // Under content.entities[i].offset/.length (type == TextEntityTypeUrl)
    val urlsProcessed:  Boolean = false,

    val sharedContacts:  List<Pair<Int, Int>>? = null,

    // MessagePhoto:
    // if mediaAlbumId != 0, there are several photos in a message. Can be grouped by mediaAlbumId
    val remotePhotoId: String? = null,     //Message.content.photo.sizes[i].remote.id; We can download the photo using this id
    val localPhotoPath: String? = null,    //Message.content.photo.sizes[i].local.path or onUpdateFile.local.path
    val photoSize: Long? = null,           //UpdateFile.local.downloadedSize
    val photoDownloaded:  Boolean = false,
    val photoProcessed:  Boolean = false,
    // Note that "sizes" is a vector, there are several sizes for each file. Need to choose what to download
    // (they have different Thumbnail types - https://core.telegram.org/constructor/photoSize)

    //MessageVoiceNote:
    val remoteVoiceNoteId: String? = null, // Message.content.voiceNote.voice.remote.id
    val voiceNoteProcessed: Boolean = false,
    val voiceNoteTranscript: String? = null, //Provided by Vosk (VoiceToTextRepository)

    //MessageDocument:
    // if mediaAlbumId != 0, there are several files in a message. Can be grouped by mediaAlbumId
    val remoteDocumentId: String? = null,  //Message.content.document.document.remote.id
    val localDocumentPath: String? = null, //Message.content.document.document.local.path or onUpdateFile.local.path
    val documentSize: Long? = null,        //Message.content.document.document.document.size
    val documentDownloaded:  Boolean = false,
    val documentProcessed:  Boolean = false,

    // Analysis related:
    val dangerLevel: DangerLevel? = null,      // null if not yet processed
    val messageAction: MessageAction? = null,  // null if not yet processed
    val relativeNotificationChecked: Boolean = false,   // Checked if the relative should be notified
    val notifiedRelative: Boolean = false,              // Was relative actually notified
    val notifiedUser: Boolean = false, // was notification created, to avoid multiple notifications

    val labels: Set<LabelType> = emptySet(),

    val createdAt: Long = unixSecondsNow()
)
fun getMaxDanger(dangerLevels: List<DangerLevel>): DangerLevel {
    return dangerLevels.maxByOrNull { it.ordinal }?: DangerLevel.SAFE
}

enum class MessageAction {
    DELETE,
    VIEWED,
    BLOCK,
    IGNORED,
    AWAITS_USER,
    SKIPPED
}
enum class DangerLevel {
    SAFE,
    SUSPICIOUS,
    CRITICAL,
}
enum class ProcessFlag { URL, TEXT, DOCUMENT, PHOTO }