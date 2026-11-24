package com.wifikidscontrol.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wifikidscontrol.model.NetworkEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM networkevents ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<NetworkEvent>>

    @Query("SELECT * FROM networkevents WHERE deviceMac = :mac ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsForDevice(mac: String, limit: Int = 50): Flow<List<NetworkEvent>>

    @Query("SELECT * FROM networkevents WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getEventsSince(since: String): Flow<List<NetworkEvent>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: NetworkEvent)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<NetworkEvent>)

    @Query("DELETE FROM networkevents WHERE timestamp < :cutoff")
    suspend fun cleanupOldEvents(cutoff: String): Int

    @Query("SELECT COUNT(*) FROM networkevents WHERE deviceMac = :mac AND eventType = 'BLOCKED' AND timestamp > :since")
    suspend fun getBlockCountForDevice(mac: String, since: String): Int
}