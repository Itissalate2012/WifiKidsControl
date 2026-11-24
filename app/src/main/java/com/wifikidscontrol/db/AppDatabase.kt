package com.wifikidscontrol.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wifikidscontrol.model.Device
import com.wifikidscontrol.model.DeviceProfile
import com.wifikidscontrol.model.NetworkEvent
import com.wifikidscontrol.model.ScheduleRule
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [Device::class, DeviceProfile::class, NetworkEvent::class, ScheduleRule::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun profileDao(): ProfileDao
    abstract fun eventDao(): EventDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wifi_kids_control.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @androidx.room.TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @androidx.room.TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }

    @androidx.room.TypeConverter
    fun fromSetInt(value: Set<Int>?): String? = value?.let { json.encodeToString(it) }

    @androidx.room.TypeConverter
    fun toSetInt(value: String?): Set<Int>? = value?.let { json.decodeFromString(it) }

    @androidx.room.TypeConverter
    fun fromDeviceType(value: com.wifikidscontrol.model.DeviceType?): String? = value?.name

    @androidx.room.TypeConverter
    fun toDeviceType(value: String?): com.wifikidscontrol.model.DeviceType? = value?.let {
        com.wifikidscontrol.model.DeviceType.valueOf(it)
    }

    @androidx.room.TypeConverter
    fun fromScheduleAction(value: com.wifikidscontrol.model.ScheduleAction?): String? = value?.name

    @androidx.room.TypeConverter
    fun toScheduleAction(value: String?): com.wifikidscontrol.model.ScheduleAction? = value?.let {
        com.wifikidscontrol.model.ScheduleAction.valueOf(it)
    }

    @androidx.room.TypeConverter
    fun fromEventType(value: com.wifikidscontrol.model.NetworkEvent.EventType?): String? = value?.name

    @androidx.room.TypeConverter
    fun toEventType(value: String?): com.wifikidscontrol.model.NetworkEvent.EventType? = value?.let {
        com.wifikidscontrol.model.NetworkEvent.EventType.valueOf(it)
    }
}