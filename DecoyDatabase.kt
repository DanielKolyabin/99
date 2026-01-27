package org.maksec.shared.data.db.decoys

import androidx.room.*
import kotlinx.serialization.json.Json

@Database(
    entities = [Decoy::class, Detection::class],
    version = 2
)
@TypeConverters(DecoysConverters::class)
@ConstructedBy(DecoysDatabaseConstructor::class)
abstract class DecoysDatabase : RoomDatabase() {
    abstract fun decoysDao(): DecoysDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT") // Actual objects are auto generated
expect object DecoysDatabaseConstructor : RoomDatabaseConstructor<DecoysDatabase>

class DecoysConverters {
    @TypeConverter
    fun fromDocTypeList(list: List<DocType>?): String? =
        list?.joinToString(",") { it.name }

    @TypeConverter
    fun toDocTypeList(data: String?): List<DocType> =
        data?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { runCatching { DocType.valueOf(it) }.getOrNull() }
            ?: emptyList()

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}