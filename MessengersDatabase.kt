package org.maksec.shared.data.db.messengers

import androidx.room.*
import org.maksec.shared.screens.util.LabelType

@Database(
    entities = [Message::class, MessengerUser::class, Chat::class, MessageStats::class],
    version = 2
)
@TypeConverters(Converters::class)
@ConstructedBy(MessengersDatabaseConstructor::class)
abstract class MessengersDatabase: RoomDatabase() {
    abstract fun messengersDao(): MessengersDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT") // Actual objects are auto generated
expect object MessengersDatabaseConstructor : RoomDatabaseConstructor<MessengersDatabase>

class Converters {
    @TypeConverter
    fun fromUrlPairs(pairs: List<Pair<Int, Int>>?): String? =
        pairs?.joinToString(";") { "${it.first},${it.second}" }

    @TypeConverter
    fun toUrlPairs(data: String?): List<Pair<Int, Int>>? =
        data?.takeIf { it.isNotBlank() }
            ?.split(";")
            ?.mapNotNull {
                val parts = it.split(",")
                if (parts.size == 2) parts[0].toIntOrNull()?.let { f ->
                    parts[1].toIntOrNull()?.let { s -> Pair(f, s) }
                } else null
            }
    @TypeConverter
    fun fromTag(tag: Tag?): String? = tag?.name

    @TypeConverter
    fun toTag(value: String?): Tag? = value?.let { Tag.valueOf(it) }

    @TypeConverter
    fun fromLabelTypeSet(set: Set<LabelType>?): String? =
        set?.joinToString(",") { it.name }

    @TypeConverter
    fun toLabelTypeSet(data: String?): Set<LabelType> =
        data?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { runCatching { LabelType.valueOf(it) }.getOrNull() }?.toSet()
            ?: emptySet()

    @TypeConverter
    fun toDangerLevel(value: Int?): DangerLevel? {
        return value?.let { DangerLevel.entries[it] }
    }

    @TypeConverter
    fun fromDangerLevel(level: DangerLevel?): Int? {
        return level?.ordinal
    }
}