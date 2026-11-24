package com.wifikidscontrol.db

import androidx.room.Dao
import androidx.room.Query
import com.wifikidscontrol.model.ScheduleRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedulerules WHERE profileId = :profileId")
    fun getRulesForProfile(profileId: String): Flow<List<ScheduleRule>>

    @Query("SELECT * FROM schedulerules")
    fun getAllRules(): Flow<List<ScheduleRule>>
}