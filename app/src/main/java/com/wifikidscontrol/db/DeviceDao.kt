package com.wifikidscontrol.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wifikidscontrol.model.Device
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE macAddress = :mac")
    fun getDevice(mac: String): Flow<Device?>

    @Query("SELECT * FROM devices WHERE isOnline = 1 ORDER BY lastSeen DESC")
    fun getOnlineDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE profileId = :profileId")
    fun getDevicesByProfile(profileId: String): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE isBlocked = 1")
    fun getBlockedDevices(): Flow<List<Device>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<Device>)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET isBlocked = :blocked WHERE macAddress = :mac")
    suspend fun setBlocked(mac: String, blocked: Boolean): Int

    @Query("UPDATE devices SET profileId = :profileId WHERE macAddress = :mac")
    suspend fun setProfile(mac: String, profileId: String): Int

    @Query("UPDATE devices SET isOnline = :online, lastSeen = :now WHERE macAddress = :mac")
    suspend fun setOnline(mac: String, online: Boolean, now: String): Int

    @Query("UPDATE devices SET ipAddress = :ip, vendor = :vendor, lastSeen = :now WHERE macAddress = :mac")
    suspend fun updateNetworkInfo(mac: String, ip: String?, vendor: String?, now: String): Int

    @Query("DELETE FROM devices WHERE macAddress = :mac")
    suspend fun deleteDevice(mac: String): Int

    @Query("DELETE FROM devices WHERE lastSeen < :cutoff")
    suspend fun cleanupOldDevices(cutoff: String): Int
}