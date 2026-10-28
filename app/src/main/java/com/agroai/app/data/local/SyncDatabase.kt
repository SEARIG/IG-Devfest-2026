package com.agroai.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "sync_records",
    primaryKeys = ["ownerUid", "collection", "recordId"],
    indices = [Index(value = ["ownerUid", "state"]), Index(value = ["ownerUid", "updatedAt"])],
)
data class SyncRecordEntity(
    val ownerUid: String,
    val collection: String,
    val recordId: String,
    val operation: String,
    val payload: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val state: String,
    val updatedAt: Long,
    val lastAttemptAt: Long? = null,
    val error: String? = null,
    val conflictPayload: String? = null,
    val conflictRevision: Long? = null,
    val conflictOperation: String? = null,
)

@Entity(tableName = "sync_state", primaryKeys = ["ownerUid", "collection"], indices = [Index("ownerUid")])
data class SyncStateEntity(
    val ownerUid: String,
    val collection: String,
    val lastServerSeconds: Long = 0,
    val lastServerNanos: Int = 0,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

@Entity(tableName = "cached_farmer_profiles")
data class CachedFarmerProfileEntity(
    @androidx.room.PrimaryKey val uid: String,
    val name: String,
    val phoneNumber: String,
    val preferredLanguage: String,
    val state: String,
    val district: String,
    val village: String,
    val area: Double,
    val areaUnit: String,
    val soilType: String,
    val irrigationType: String,
    val currentCrops: String,
    val goals: String,
    val experienceLevel: String,
    val onboardingCompleted: Boolean,
    val createdAtSeconds: Long?,
    val createdAtNanos: Int?,
    val updatedAtSeconds: Long?,
    val updatedAtNanos: Int?,
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_records WHERE ownerUid = :ownerUid ORDER BY updatedAt")
    fun observeRecords(ownerUid: String): Flow<List<SyncRecordEntity>>

    @Query("SELECT * FROM sync_records WHERE ownerUid = :ownerUid ORDER BY updatedAt")
    suspend fun all(ownerUid: String): List<SyncRecordEntity>

    @Query("SELECT * FROM sync_records WHERE ownerUid = :ownerUid AND state IN ('pending', 'failed') ORDER BY updatedAt LIMIT :limit")
    suspend fun pending(ownerUid: String, limit: Int = 100): List<SyncRecordEntity>

    @Query("SELECT * FROM sync_records WHERE ownerUid = :ownerUid AND collection = :collection AND recordId = :recordId")
    suspend fun record(ownerUid: String, collection: String, recordId: String): SyncRecordEntity?

    @Upsert suspend fun upsert(record: SyncRecordEntity)

    @Query("DELETE FROM sync_records WHERE ownerUid = :ownerUid AND collection = :collection AND recordId = :recordId")
    suspend fun delete(ownerUid: String, collection: String, recordId: String)

    @Query("SELECT * FROM sync_state WHERE ownerUid = :ownerUid AND collection = :collection")
    suspend fun state(ownerUid: String, collection: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE ownerUid = :ownerUid")
    fun observeStates(ownerUid: String): Flow<List<SyncStateEntity>>

    @Upsert suspend fun upsertState(state: SyncStateEntity)

    @Query("UPDATE sync_records SET state = 'pending', error = NULL WHERE ownerUid = :ownerUid AND state = 'failed'")
    suspend fun retryFailed(ownerUid: String)

    @Query("SELECT * FROM cached_farmer_profiles WHERE uid = :uid")
    suspend fun cachedProfile(uid: String): CachedFarmerProfileEntity?

    @Upsert suspend fun cacheProfile(profile: CachedFarmerProfileEntity)

    @Query("SELECT COUNT(*) FROM farm_plots WHERE ownerUid = :ownerUid")
    suspend fun plotCount(ownerUid: String): Int

    @Query("SELECT COUNT(*) FROM ai_conversations WHERE ownerUid = :ownerUid")
    suspend fun conversationCount(ownerUid: String): Int

    @Query("SELECT COUNT(*) FROM crop_inspections WHERE ownerUid = :ownerUid")
    suspend fun inspectionCount(ownerUid: String): Int

    @Query("SELECT COUNT(*) FROM harvest_records WHERE ownerUid = :ownerUid")
    suspend fun harvestCount(ownerUid: String): Int

    @Query("UPDATE farm_plots SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun movePlots(oldOwner: String, newOwner: String)
    @Query("UPDATE farm_boundaries SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveBoundaries(oldOwner: String, newOwner: String)
    @Query("UPDATE crop_plans SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun movePlans(oldOwner: String, newOwner: String)
    @Query("UPDATE calendar_activities SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveActivities(oldOwner: String, newOwner: String)
    @Query("UPDATE ai_conversations SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveConversations(oldOwner: String, newOwner: String)
    @Query("UPDATE chat_messages SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveMessages(oldOwner: String, newOwner: String)
    @Query("UPDATE crop_inspections SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveInspections(oldOwner: String, newOwner: String)
    @Query("UPDATE crop_symptoms SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveSymptoms(oldOwner: String, newOwner: String)
    @Query("UPDATE harvest_records SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveHarvests(oldOwner: String, newOwner: String)
    @Query("UPDATE actual_expenses SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveExpenses(oldOwner: String, newOwner: String)
    @Query("UPDATE farm_alerts SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveAlerts(oldOwner: String, newOwner: String)
    @Query("UPDATE weather_snapshots SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveWeather(oldOwner: String, newOwner: String)
    @Query("UPDATE market_prices SET ownerUid = :newOwner WHERE ownerUid = :oldOwner") suspend fun moveMarket(oldOwner: String, newOwner: String)
}
