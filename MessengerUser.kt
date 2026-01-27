package org.maksec.shared.data.db.messengers

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.maksec.shared.screens.util.MessengerSource

fun MessengerUser.generateReadableName(): String {
    return if ((firstName != null) || (lastName != null))
    "$firstName  $lastName"
    else userName?: phoneNumber?: userId.toString()
}

@Entity
data class MessengerUser(
    @PrimaryKey val userId: Long,
    val source: MessengerSource,
    var userName: String?,
    var firstName: String? = null,
    var lastName: String? = null,
    var phoneNumber: String? = null,
    var pfpImageId: Long? = null,
    var pfpImageBytes: ByteArray? = null,
    var isContact: Boolean,

    // Custom Data:
    var isIgnored: Boolean = false,
    var isBlocked: Boolean = false,
    val tag: Tag? = null,          //null if not tagged, this is user's current tag
    val spamTags: Int = 0,
    val scamTags: Int = 0,
    val safeTags: Int = 0,
    val adTags: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MessengerUser

        if (userId != other.userId) return false
        if (pfpImageId != other.pfpImageId) return false
        if (isContact != other.isContact) return false
        if (isIgnored != other.isIgnored) return false
        if (isBlocked != other.isBlocked) return false
        if (spamTags != other.spamTags) return false
        if (scamTags != other.scamTags) return false
        if (safeTags != other.safeTags) return false
        if (adTags != other.adTags) return false
        if (source != other.source) return false
        if (userName != other.userName) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (phoneNumber != other.phoneNumber) return false
        if (tag != other.tag) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + (pfpImageId?.hashCode() ?: 0)
        result = 31 * result + isContact.hashCode()
        result = 31 * result + isIgnored.hashCode()
        result = 31 * result + isBlocked.hashCode()
        result = 31 * result + spamTags
        result = 31 * result + scamTags
        result = 31 * result + safeTags
        result = 31 * result + adTags
        result = 31 * result + source.hashCode()
        result = 31 * result + (userName?.hashCode() ?: 0)
        result = 31 * result + (firstName?.hashCode() ?: 0)
        result = 31 * result + (lastName?.hashCode() ?: 0)
        result = 31 * result + (phoneNumber?.hashCode() ?: 0)
        result = 31 * result + (tag?.hashCode() ?: 0)
        return result
    }
}

enum class Tag {
    SPAM,
    AD,
    SCAM,
    SAFE
}
