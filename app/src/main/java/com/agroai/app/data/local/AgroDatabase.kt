package com.agroai.app.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "farm_plots", indices = [Index("ownerUid")])
data class FarmPlotEntity(@PrimaryKey val id: String, val name: String, val area: Double, val areaUnit: String, val soilType: String, val irrigationMethod: String, val cropHistory: String, val updatedAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "farm_boundaries", foreignKeys = [ForeignKey(entity = FarmPlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)], indices = [Index("plotId", unique = true), Index("ownerUid")])
data class FarmBoundaryEntity(@PrimaryKey val id: String, val plotId: String, val encodedPoints: String, val areaSquareMeters: Double, val accuracyMeters: Float?, val updatedAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "crop_plans", foreignKeys = [ForeignKey(entity = FarmPlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.CASCADE)], indices = [Index("plotId"), Index("ownerUid")])
data class CropPlanEntity(@PrimaryKey val id: String, val plotId: String, val cropId: String, val sowingEpochDay: Long, val areaHectares: Double, val irrigationMethod: String, val seedVariety: String, val status: String, val seedQuantityKgLow: Double, val seedQuantityKgHigh: Double, val harvestStartEpochDay: Long, val harvestEndEpochDay: Long, val updatedAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "calendar_activities", indices = [Index("planId"), Index("plotId"), Index("scheduledAt"), Index("ownerUid")])
data class CalendarActivityEntity(@PrimaryKey val id: String, val planId: String?, val plotId: String?, val type: String, val title: String, val scheduledAt: Long, val notes: String, val completed: Boolean, val reminderEnabled: Boolean, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Dao
interface FarmDao {
    @Query("SELECT * FROM farm_plots WHERE ownerUid = :ownerUid ORDER BY updatedAt DESC") fun observePlots(ownerUid: String): Flow<List<FarmPlotEntity>>
    @Query("SELECT * FROM farm_boundaries WHERE ownerUid = :ownerUid") fun observeBoundaries(ownerUid: String): Flow<List<FarmBoundaryEntity>>
    @Query("SELECT * FROM crop_plans WHERE ownerUid = :ownerUid ORDER BY updatedAt DESC") fun observePlans(ownerUid: String): Flow<List<CropPlanEntity>>
    @Query("SELECT * FROM calendar_activities WHERE ownerUid = :ownerUid ORDER BY scheduledAt") fun observeActivities(ownerUid: String): Flow<List<CalendarActivityEntity>>
    @Query("SELECT * FROM calendar_activities WHERE ownerUid = :ownerUid AND reminderEnabled = 1 AND completed = 0 AND scheduledAt > :after") suspend fun upcomingReminders(ownerUid: String, after: Long): List<CalendarActivityEntity>
    @Query("SELECT * FROM farm_plots WHERE ownerUid = :ownerUid") suspend fun plots(ownerUid: String): List<FarmPlotEntity>
    @Query("SELECT * FROM farm_boundaries WHERE ownerUid = :ownerUid") suspend fun boundaries(ownerUid: String): List<FarmBoundaryEntity>
    @Query("SELECT * FROM crop_plans WHERE ownerUid = :ownerUid") suspend fun plans(ownerUid: String): List<CropPlanEntity>
    @Query("SELECT * FROM calendar_activities WHERE ownerUid = :ownerUid") suspend fun activities(ownerUid: String): List<CalendarActivityEntity>

    @Upsert suspend fun upsertPlot(plot: FarmPlotEntity)
    @Upsert suspend fun upsertBoundary(boundary: FarmBoundaryEntity)
    @Upsert suspend fun upsertPlan(plan: CropPlanEntity)
    @Upsert suspend fun upsertActivities(activities: List<CalendarActivityEntity>)
    @Upsert suspend fun upsertActivity(activity: CalendarActivityEntity)
    @Query("DELETE FROM farm_plots WHERE id = :id") suspend fun deletePlot(id: String)
    @Query("DELETE FROM farm_boundaries WHERE plotId = :plotId") suspend fun deleteBoundary(plotId: String)
    @Query("DELETE FROM farm_boundaries WHERE id = :id") suspend fun deleteBoundaryById(id: String)
    @Query("DELETE FROM crop_plans WHERE id = :id") suspend fun deletePlan(id: String)
    @Query("DELETE FROM calendar_activities WHERE id = :id") suspend fun deleteActivity(id: String)
    @Query("DELETE FROM calendar_activities WHERE planId = :planId") suspend fun deleteActivitiesForPlan(planId: String)
    @Query("DELETE FROM calendar_activities WHERE plotId = :plotId") suspend fun deleteActivitiesForPlot(plotId: String)
    @Query("DELETE FROM calendar_activities WHERE ownerUid = :ownerUid") suspend fun deleteActivitiesForOwner(ownerUid: String)
    @Query("DELETE FROM farm_plots WHERE ownerUid = :ownerUid") suspend fun deletePlotsForOwner(ownerUid: String)

    @Transaction
    suspend fun savePlanWithActivities(plan: CropPlanEntity, activities: List<CalendarActivityEntity>) {
        upsertPlan(plan)
        deleteActivitiesForPlan(plan.id)
        upsertActivities(activities)
    }

    @Transaction
    suspend fun deletePlotData(plotId: String) {
        deleteActivitiesForPlot(plotId)
        deletePlot(plotId)
    }

    @Transaction suspend fun clearOwner(ownerUid: String) {
        deleteActivitiesForOwner(ownerUid)
        deletePlotsForOwner(ownerUid)
    }
}

@Database(
    entities = [
        FarmPlotEntity::class, FarmBoundaryEntity::class, CropPlanEntity::class, CalendarActivityEntity::class,
        AiConversationEntity::class, ChatMessageEntity::class, CropInspectionEntity::class, CropSymptomEntity::class,
        HarvestRecordEntity::class, ActualExpenseEntity::class, FarmAlertEntity::class, WeatherSnapshotEntity::class, MarketPriceEntity::class,
        SyncRecordEntity::class, SyncStateEntity::class, CachedFarmerProfileEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AgroDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
    abstract fun stage3Dao(): Stage3Dao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile private var instance: AgroDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `cardTitle` TEXT, `cardLines` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `ai_conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` ON `chat_messages` (`conversationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `crop_inspections` (`id` TEXT NOT NULL, `plotId` TEXT, `imageUri` TEXT, `cropName` TEXT NOT NULL, `affectedParts` TEXT NOT NULL, `observations` TEXT NOT NULL, `possibleCauses` TEXT NOT NULL, `evidenceNeeded` TEXT NOT NULL, `nextSteps` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `imageModelUsed` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`plotId`) REFERENCES `farm_plots`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_inspections_plotId` ON `crop_inspections` (`plotId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_inspections_createdAt` ON `crop_inspections` (`createdAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `crop_symptoms` (`id` TEXT NOT NULL, `inspectionId` TEXT NOT NULL, `code` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`inspectionId`) REFERENCES `crop_inspections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_crop_symptoms_inspectionId` ON `crop_symptoms` (`inspectionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `harvest_records` (`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `actualHarvestEpochDay` INTEGER NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `sellingPricePerUnit` REAL, `actualTotalExpenses` REAL NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`planId`) REFERENCES `crop_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_harvest_records_planId` ON `harvest_records` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_harvest_records_actualHarvestEpochDay` ON `harvest_records` (`actualHarvestEpochDay`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `actual_expenses` (`id` TEXT NOT NULL, `harvestId` TEXT NOT NULL, `label` TEXT NOT NULL, `amount` REAL NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`harvestId`) REFERENCES `harvest_records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_actual_expenses_harvestId` ON `actual_expenses` (`harvestId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `farm_alerts` (`id` TEXT NOT NULL, `sourceKey` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `relatedPlanId` TEXT, `relatedPlotId` TEXT, `priority` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, `read` INTEGER NOT NULL, `action` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_farm_alerts_sourceKey` ON `farm_alerts` (`sourceKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_alerts_scheduledAt` ON `farm_alerts` (`scheduledAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_farm_alerts_read` ON `farm_alerts` (`read`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `weather_snapshots` (`id` TEXT NOT NULL, `location` TEXT NOT NULL, `observedAt` INTEGER NOT NULL, `forecastFor` INTEGER NOT NULL, `temperatureC` REAL NOT NULL, `humidityPercent` REAL NOT NULL, `windKph` REAL NOT NULL, `rainMillimetres` REAL NOT NULL, `condition` TEXT NOT NULL, `source` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weather_snapshots_forecastFor` ON `weather_snapshots` (`forecastFor`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `market_prices` (`id` TEXT NOT NULL, `crop` TEXT NOT NULL, `state` TEXT NOT NULL, `district` TEXT NOT NULL, `market` TEXT NOT NULL, `minPricePerQuintal` REAL NOT NULL, `maxPricePerQuintal` REAL NOT NULL, `modalPricePerQuintal` REAL NOT NULL, `dataDateEpochDay` INTEGER NOT NULL, `source` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_prices_crop_state_district_market_dataDateEpochDay` ON `market_prices` (`crop`, `state`, `district`, `market`, `dataDateEpochDay`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_prices_dataDateEpochDay` ON `market_prices` (`dataDateEpochDay`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "farm_plots", "farm_boundaries", "crop_plans", "calendar_activities",
                    "ai_conversations", "chat_messages", "crop_inspections", "crop_symptoms",
                    "harvest_records", "actual_expenses", "farm_alerts", "weather_snapshots", "market_prices",
                ).forEach { table -> db.execSQL("ALTER TABLE `$table` ADD COLUMN `ownerUid` TEXT NOT NULL DEFAULT 'local'") }
                db.execSQL("DROP INDEX IF EXISTS `index_farm_alerts_sourceKey`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_farm_alerts_ownerUid_sourceKey` ON `farm_alerts` (`ownerUid`, `sourceKey`)")
                db.execSQL("DROP INDEX IF EXISTS `index_market_prices_crop_state_district_market_dataDateEpochDay`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_market_prices_ownerUid_crop_state_district_market_dataDateEpochDay` ON `market_prices` (`ownerUid`, `crop`, `state`, `district`, `market`, `dataDateEpochDay`)")
                listOf(
                    "farm_plots", "farm_boundaries", "crop_plans", "calendar_activities", "ai_conversations",
                    "chat_messages", "crop_inspections", "crop_symptoms", "harvest_records", "actual_expenses",
                    "farm_alerts", "weather_snapshots", "market_prices",
                ).forEach { table -> db.execSQL("CREATE INDEX IF NOT EXISTS `index_${table}_ownerUid` ON `$table` (`ownerUid`)") }
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_records` (`ownerUid` TEXT NOT NULL, `collection` TEXT NOT NULL, `recordId` TEXT NOT NULL, `operation` TEXT NOT NULL, `payload` TEXT NOT NULL, `localRevision` INTEGER NOT NULL, `remoteRevision` INTEGER NOT NULL, `state` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `lastAttemptAt` INTEGER, `error` TEXT, `conflictPayload` TEXT, `conflictRevision` INTEGER, `conflictOperation` TEXT, PRIMARY KEY(`ownerUid`, `collection`, `recordId`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_records_ownerUid_state` ON `sync_records` (`ownerUid`, `state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_records_ownerUid_updatedAt` ON `sync_records` (`ownerUid`, `updatedAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`ownerUid` TEXT NOT NULL, `collection` TEXT NOT NULL, `lastServerSeconds` INTEGER NOT NULL, `lastServerNanos` INTEGER NOT NULL, `lastSyncAt` INTEGER, `lastError` TEXT, PRIMARY KEY(`ownerUid`, `collection`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_state_ownerUid` ON `sync_state` (`ownerUid`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `cached_farmer_profiles` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `preferredLanguage` TEXT NOT NULL, `state` TEXT NOT NULL, `district` TEXT NOT NULL, `village` TEXT NOT NULL, `area` REAL NOT NULL, `areaUnit` TEXT NOT NULL, `soilType` TEXT NOT NULL, `irrigationType` TEXT NOT NULL, `currentCrops` TEXT NOT NULL, `goals` TEXT NOT NULL, `experienceLevel` TEXT NOT NULL, `onboardingCompleted` INTEGER NOT NULL, `createdAtSeconds` INTEGER, `createdAtNanos` INTEGER, `updatedAtSeconds` INTEGER, `updatedAtNanos` INTEGER, PRIMARY KEY(`uid`))")
            }
        }

        fun get(context: Context): AgroDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AgroDatabase::class.java, "agro-ai-local.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}

internal fun FarmPlotEntity.toDomain() = FarmPlot(id, name, area, areaUnit, soilType, irrigationMethod, cropHistory, updatedAt)
internal fun FarmPlot.toEntity(ownerUid: String = "local") = FarmPlotEntity(id, name, area, areaUnit, soilType, irrigationMethod, cropHistory, updatedAt, ownerUid)

internal fun FarmBoundaryEntity.toDomain() = FarmBoundary(id, plotId, decodePoints(encodedPoints), areaSquareMeters, accuracyMeters, updatedAt)
internal fun FarmBoundary.toEntity(ownerUid: String = "local") = FarmBoundaryEntity(id, plotId, points.joinToString(";") { "${it.latitude},${it.longitude}" }, areaSquareMeters, accuracyMeters, updatedAt, ownerUid)

private fun decodePoints(value: String) = value.split(';').mapNotNull { item ->
    val pair = item.split(',')
    if (pair.size == 2) pair[0].toDoubleOrNull()?.let { lat -> pair[1].toDoubleOrNull()?.let { lon -> GeoPoint(lat, lon) } } else null
}

internal fun CropPlanEntity.toDomain() = CropPlan(id, plotId, cropId, java.time.LocalDate.ofEpochDay(sowingEpochDay), areaHectares, irrigationMethod, seedVariety, status, seedQuantityKgLow, seedQuantityKgHigh, java.time.LocalDate.ofEpochDay(harvestStartEpochDay), java.time.LocalDate.ofEpochDay(harvestEndEpochDay), updatedAt)
internal fun CropPlan.toEntity(ownerUid: String = "local") = CropPlanEntity(id, plotId, cropId, sowingDate.toEpochDay(), areaHectares, irrigationMethod, seedVariety, status, seedQuantityKgLow, seedQuantityKgHigh, harvestStart.toEpochDay(), harvestEnd.toEpochDay(), updatedAt, ownerUid)

internal fun CalendarActivityEntity.toDomain() = CropPlanActivity(id, planId, plotId, type, title, scheduledAt, notes, completed, reminderEnabled)
internal fun CropPlanActivity.toEntity(ownerUid: String = "local") = CalendarActivityEntity(id, planId, plotId, type, title, scheduledAt, notes, completed, reminderEnabled, ownerUid)
