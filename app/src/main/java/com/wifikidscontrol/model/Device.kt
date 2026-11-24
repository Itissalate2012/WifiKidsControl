package com.wifikidscontrol.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.wifikidscontrol.db.Converters
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "devices")
data class Device(
    @PrimaryKey
    val macAddress: String,
    val name: String,
    val vendor: String? = null,
    val ipAddress: String? = null,
    val profileId: String? = null,
    @TypeConverters(Converters::class)
    val firstSeen: LocalDateTime = LocalDateTime.now(),
    @TypeConverters(Converters::class)
    val lastSeen: LocalDateTime = LocalDateTime.now(),
    var isBlocked: Boolean = false,
    var isOnline: Boolean = false,
    var currentProfile: String = "default",
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val notes: String = ""
) {
    companion object {
        fun unknown(mac: String, ip: String? = null): Device {
            return Device(
                macAddress = mac.uppercase(),
                name = "Appareil ${mac.uppercase().takeLast(5).replace(":", "")}",
                ipAddress = ip,
                vendor = resolveVendor(mac)
            )
        }

        private fun resolveVendor(mac: String): String? {
            val oui = mac.uppercase().take(8).replace(":", "")
            return when (oui) {
                "001A2B", "001B63", "001C42", "001D0F", "001E52", "001F3B", "0021E9", "002241",
                "002312", "0023DF", "002436", "002500", "002608", "0026B0", "0026BB", "0026C6",
                "0026C7", "0026F2", "0026F3", "00270E", "002710", "002719", "002722" -> "Apple"
                "B827EB", "DC2A2B", "E45F01" -> "Raspberry Pi"
                "000C29", "001C14", "005056" -> "VMware"
                "00163E", "00255E" -> "Xen/QEMU"
                "00155D" -> "Microsoft Hyper-V"
                "001B21", "001C42", "001D0F", "001E52", "001F3B", "0021E9", "002241",
                "002312", "0023DF", "002436", "002500", "002608", "0026B0", "0026BB", "0026C6",
                "0026C7", "0026F2", "0026F3", "00270E", "002710", "002719", "002722",
                "005056", "000C29", "00163E", "00255E", "00155D" -> "Virtual"
                "FCFC48", "AC87A3", "84D6D0" -> "Amazon"
                "D05FB8", "F4F5E8", "F4F5E8" -> "Google Nest"
                "18B430", "34D270", "503EA" -> "Samsung"
                "F01898", "F01898" -> "Xiaomi"
                else -> null
            }
        }
    }

    val displayName: String
        get() = if (name.isNotBlank()) name else "Appareil ${macAddress.takeLast(5).replace(":", "")}"

    val shortMac: String
        get() = macAddress.uppercase().takeLast(5).replace(":", "")
}

enum class DeviceType {
    PHONE, TABLET, LAPTOP, DESKTOP, TV, CONSOLE, IOT, ROUTER, UNKNOWN
}

@Serializable
data class DeviceProfile(
    val id: String,
    val name: String,
    val description: String,
    val blockedByDefault: Boolean = false,
    val scheduleRules: List<ScheduleRule> = emptyList(),
    val maxDailyMinutes: Int? = null,
    val blockedCategories: List<String> = emptyList(),
    val color: Int = 0xFF009688 // Teal default
) {
    companion object {
        val DEFAULT = DeviceProfile(
            id = "default",
            name = "Standard",
            description = "Accès normal sans restrictions",
            blockedByDefault = false
        )
        val CHILD = DeviceProfile(
            id = "child",
            name = "Enfant",
            description = "Contrôle parental strict",
            blockedByDefault = false,
            scheduleRules = listOf(
                ScheduleRule(
                    id = "bedtime",
                    name = "Heure du coucher",
                    days = setOf(1,2,3,4,5,6,7),
                    startTime = "21:00",
                    endTime = "07:00",
                    action = ScheduleAction.BLOCK
                ),
                ScheduleRule(
                    id = "homework",
                    name = "Devoirs",
                    days = setOf(1,2,3,4,5),
                    startTime = "17:00",
                    endTime = "19:00",
                    action = ScheduleAction.BLOCK
                )
            ),
            maxDailyMinutes = 120,
            color = 0xFFE91E63 // Pink
        )
        val GUEST = DeviceProfile(
            id = "guest",
            name = "Invité",
            description = "Accès limité temporaire",
            blockedByDefault = false,
            scheduleRules = listOf(
                ScheduleRule(
                    id = "guest_hours",
                    name = "Heures invités",
                    days = setOf(1,2,3,4,5,6,7),
                    startTime = "23:00",
                    endTime = "08:00",
                    action = ScheduleAction.BLOCK
                )
            ),
            maxDailyMinutes = 240,
            color = 0xFF2196F3 // Blue
        )
        val ADULT = DeviceProfile(
            id = "adult",
            name = "Adulte",
            description = "Accès complet sans restriction",
            blockedByDefault = false,
            color = 0xFF4CAF50 // Green
        )

        val ALL = listOf(DEFAULT, CHILD, GUEST, ADULT)
        fun getById(id: String) = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}

@Serializable
data class ScheduleRule(
    val id: String,
    val name: String,
    val days: Set<Int>, // 1=Mon ... 7=Sun
    val startTime: String, // HH:mm
    val endTime: String, // HH:mm
    val action: ScheduleAction = ScheduleAction.BLOCK
) {
    fun isActiveNow(): Boolean {
        val now = java.time.LocalTime.now()
        val start = java.time.LocalTime.parse(startTime)
        val end = java.time.LocalTime.parse(endTime)
        val today = java.time.LocalDate.now().dayOfWeek.value // 1=Mon

        val isTodayActive = today in days
        val isTimeActive = if (start <= end) {
            now >= start && now <= end
        } else { // overnight
            now >= start || now <= end
        }
        return isTodayActive && isTimeActive
    }

    val displayTime: String
        get() = "${formatTime(startTime)} - ${formatTime(endTime)}"

    private fun formatTime(t: String): String {
        val parts = t.split(":")
        return "${parts[0]}h${parts[1]}"
    }
}

enum class ScheduleAction {
    BLOCK, ALLOW, THROTTLE
}

@Serializable
data class NetworkEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val deviceMac: String,
    val eventType: EventType,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val details: String = ""
) {
    enum class EventType {
        CONNECTED, DISCONNECTED, BLOCKED, UNBLOCKED, PROFILE_CHANGED, SCHEDULE_TRIGGERED
    }
}