package org.maksec.shared.data.db.incidents

import androidx.room.*
import kotlinx.serialization.json.Json
import org.maksec.shared.screens.util.LabelType

@Database(
    entities = [Signature::class, Incident::class],
    version = 1
)
@TypeConverters(IncidentConverters::class)
@ConstructedBy(IncidentDatabaseConstructor::class)
abstract class IncidentDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT") // Actual objects are auto generated
expect object IncidentDatabaseConstructor : RoomDatabaseConstructor<IncidentDatabase>

class IncidentConverters {
    @TypeConverter
    fun fromLabelTypeList(list: List<LabelType>?): String? =
        list?.joinToString(",") { it.name }

    @TypeConverter
    fun toLabelTypeList(data: String?): List<LabelType> =
        data?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { runCatching { LabelType.valueOf(it) }.getOrNull() }
            ?: emptyList()

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}