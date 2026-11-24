package com.wifikidscontrol.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wifikidscontrol.model.DeviceProfile
import com.wifikidscontrol.model.ScheduleRule
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM deviceprofiles ORDER BY id")
    fun getAllProfiles(): Flow<List<DeviceProfile>>

    @Query("SELECT * FROM deviceprofiles WHERE id = :id")
    fun getProfile(id: String): Flow<DeviceProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: DeviceProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<DeviceProfile>)

    @Update
    suspend fun updateProfile(profile: DeviceProfile)

    @Query("DELETE FROM deviceprofiles WHERE id = :id")
    suspend fun deleteProfile(id: String): Int

    // Schedule rules
    @Query("SELECT * FROM schedulerules WHERE profileId = :profileId")
    fun getRulesForProfile(profileId: String): Flow<List<ScheduleRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: ScheduleRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<ScheduleRule>)

    @Update
    suspend fun updateRule(rule: ScheduleRule)

    @Query("DELETE FROM schedulerules WHERE id = :id")
    suspend fun deleteRule(id: String): Int

    @Query("DELETE FROM schedulerules WHERE profileId = :profileId")
    suspend fun deleteRulesForProfile(profileId: String): Int
}