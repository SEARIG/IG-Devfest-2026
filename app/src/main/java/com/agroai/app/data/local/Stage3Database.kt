package com.agroai.app.data.local

import androidx.room.*
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "ai_conversations", indices = [Index("ownerUid")])
data class AiConversationEntity(@PrimaryKey val id: String, val title: String, val createdAt: Long, val updatedAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(entity = AiConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId"), Index("createdAt"), Index("ownerUid")],
)
data class ChatMessageEntity(@PrimaryKey val id: String, val conversationId: String, val role: String, val text: String, val createdAt: Long, val cardTitle: String?, val cardLines: String, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(
    tableName = "crop_inspections",
    foreignKeys = [ForeignKey(entity = FarmPlotEntity::class, parentColumns = ["id"], childColumns = ["plotId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("plotId"), Index("createdAt"), Index("ownerUid")],
)
data class CropInspectionEntity(
    @PrimaryKey val id: String,
    val plotId: String?,
    val imageUri: String?,
    val cropName: String,
    val affectedParts: String,
    val observations: String,
    val possibleCauses: String,
    val evidenceNeeded: String,
    val nextSteps: String,
    val createdAt: Long,
    val imageModelUsed: Boolean,
    @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local",
)

@Entity(
    tableName = "crop_symptoms",
    foreignKeys = [ForeignKey(entity = CropInspectionEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("inspectionId"), Index("ownerUid")],
)
data class CropSymptomEntity(@PrimaryKey val id: String, val inspectionId: String, val code: String, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

data class InspectionWithSymptoms(
    @Embedded val inspection: CropInspectionEntity,
    @Relation(parentColumn = "id", entityColumn = "inspectionId") val symptoms: List<CropSymptomEntity>,
)

@Entity(
    tableName = "harvest_records",
    foreignKeys = [ForeignKey(entity = CropPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("planId", unique = true), Index("actualHarvestEpochDay"), Index("ownerUid")],
)
data class HarvestRecordEntity(@PrimaryKey val id: String, val planId: String, val actualHarvestEpochDay: Long, val quantity: Double, val unit: String, val sellingPricePerUnit: Double?, val actualTotalExpenses: Double, val notes: String, val createdAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(
    tableName = "actual_expenses",
    foreignKeys = [ForeignKey(entity = HarvestRecordEntity::class, parentColumns = ["id"], childColumns = ["harvestId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("harvestId"), Index("ownerUid")],
)
data class ActualExpenseEntity(@PrimaryKey val id: String, val harvestId: String, val label: String, val amount: Double, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "farm_alerts", indices = [Index(value = ["ownerUid", "sourceKey"], unique = true), Index("scheduledAt"), Index("read"), Index("ownerUid")])
data class FarmAlertEntity(@PrimaryKey val id: String, val sourceKey: String, val title: String, val message: String, val relatedPlanId: String?, val relatedPlotId: String?, val priority: String, val scheduledAt: Long, val read: Boolean, val action: String, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "weather_snapshots", indices = [Index("forecastFor"), Index("ownerUid")])
data class WeatherSnapshotEntity(@PrimaryKey val id: String, val location: String, val observedAt: Long, val forecastFor: Long, val temperatureC: Double, val humidityPercent: Double, val windKph: Double, val rainMillimetres: Double, val condition: String, val source: String, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Entity(tableName = "market_prices", indices = [Index(value = ["ownerUid", "crop", "state", "district", "market", "dataDateEpochDay"], unique = true), Index("dataDateEpochDay"), Index("ownerUid")])
data class MarketPriceEntity(@PrimaryKey val id: String, val crop: String, val state: String, val district: String, val market: String, val minPricePerQuintal: Double, val maxPricePerQuintal: Double, val modalPricePerQuintal: Double, val dataDateEpochDay: Long, val source: String, val savedAt: Long, @ColumnInfo(defaultValue = "'local'") val ownerUid: String = "local")

@Dao
interface Stage3Dao {
    @Query("SELECT * FROM ai_conversations WHERE ownerUid = :ownerUid ORDER BY updatedAt DESC") fun observeConversations(ownerUid: String): Flow<List<AiConversationEntity>>
    @Query("SELECT * FROM chat_messages WHERE ownerUid = :ownerUid AND conversationId = :conversationId ORDER BY createdAt") fun observeMessages(ownerUid: String, conversationId: String): Flow<List<ChatMessageEntity>>
    @Transaction @Query("SELECT * FROM crop_inspections WHERE ownerUid = :ownerUid ORDER BY createdAt DESC") fun observeInspections(ownerUid: String): Flow<List<InspectionWithSymptoms>>
    @Query("SELECT * FROM harvest_records WHERE ownerUid = :ownerUid ORDER BY actualHarvestEpochDay DESC") fun observeHarvests(ownerUid: String): Flow<List<HarvestRecordEntity>>
    @Query("SELECT * FROM actual_expenses WHERE ownerUid = :ownerUid ORDER BY label") fun observeExpenses(ownerUid: String): Flow<List<ActualExpenseEntity>>
    @Query("SELECT * FROM farm_alerts WHERE ownerUid = :ownerUid ORDER BY read, scheduledAt DESC") fun observeAlerts(ownerUid: String): Flow<List<FarmAlertEntity>>
    @Query("SELECT * FROM weather_snapshots WHERE ownerUid = :ownerUid ORDER BY forecastFor") fun observeWeather(ownerUid: String): Flow<List<WeatherSnapshotEntity>>
    @Query("SELECT * FROM market_prices WHERE ownerUid = :ownerUid ORDER BY dataDateEpochDay DESC, modalPricePerQuintal DESC") fun observeMarketPrices(ownerUid: String): Flow<List<MarketPriceEntity>>
    @Query("SELECT * FROM ai_conversations WHERE ownerUid = :ownerUid") suspend fun conversations(ownerUid: String): List<AiConversationEntity>
    @Query("SELECT * FROM chat_messages WHERE ownerUid = :ownerUid") suspend fun messages(ownerUid: String): List<ChatMessageEntity>
    @Transaction @Query("SELECT * FROM crop_inspections WHERE ownerUid = :ownerUid") suspend fun inspections(ownerUid: String): List<InspectionWithSymptoms>
    @Query("SELECT * FROM harvest_records WHERE ownerUid = :ownerUid") suspend fun harvests(ownerUid: String): List<HarvestRecordEntity>
    @Query("SELECT * FROM actual_expenses WHERE ownerUid = :ownerUid") suspend fun expenses(ownerUid: String): List<ActualExpenseEntity>
    @Query("SELECT * FROM farm_alerts WHERE ownerUid = :ownerUid") suspend fun alerts(ownerUid: String): List<FarmAlertEntity>
    @Query("SELECT * FROM farm_alerts WHERE ownerUid = :ownerUid AND id = :id LIMIT 1") suspend fun alert(ownerUid: String, id: String): FarmAlertEntity?
    @Transaction @Query("SELECT * FROM crop_inspections WHERE ownerUid = :ownerUid AND plotId = :plotId") suspend fun inspectionsForPlot(ownerUid: String, plotId: String): List<InspectionWithSymptoms>
    @Transaction @Query("SELECT * FROM crop_inspections WHERE ownerUid = :ownerUid AND id = :id LIMIT 1") suspend fun inspection(ownerUid: String, id: String): InspectionWithSymptoms?
    @Query("SELECT * FROM harvest_records WHERE ownerUid = :ownerUid AND planId IN (:planIds)") suspend fun harvestsForPlans(ownerUid: String, planIds: List<String>): List<HarvestRecordEntity>
    @Query("SELECT * FROM actual_expenses WHERE ownerUid = :ownerUid AND harvestId IN (:harvestIds)") suspend fun expensesForHarvests(ownerUid: String, harvestIds: List<String>): List<ActualExpenseEntity>
    @Query("SELECT * FROM actual_expenses WHERE ownerUid = :ownerUid AND harvestId = :harvestId") suspend fun expensesForHarvest(ownerUid: String, harvestId: String): List<ActualExpenseEntity>

    @Upsert suspend fun upsertConversation(value: AiConversationEntity)
    @Upsert suspend fun upsertMessage(value: ChatMessageEntity)
    @Query("DELETE FROM ai_conversations WHERE id = :id") suspend fun deleteConversation(id: String)
    @Query("DELETE FROM chat_messages WHERE id = :id") suspend fun deleteMessage(id: String)
    @Upsert suspend fun upsertInspection(value: CropInspectionEntity)
    @Upsert suspend fun upsertSymptoms(values: List<CropSymptomEntity>)
    @Query("DELETE FROM crop_symptoms WHERE inspectionId = :inspectionId") suspend fun deleteSymptoms(inspectionId: String)
    @Query("DELETE FROM crop_inspections WHERE id = :id") suspend fun deleteInspection(id: String)
    @Upsert suspend fun upsertHarvest(value: HarvestRecordEntity)
    @Upsert suspend fun upsertExpenses(values: List<ActualExpenseEntity>)
    @Query("DELETE FROM actual_expenses WHERE id = :id") suspend fun deleteExpense(id: String)
    @Query("DELETE FROM actual_expenses WHERE harvestId = :harvestId") suspend fun deleteExpenses(harvestId: String)
    @Query("DELETE FROM harvest_records WHERE id = :id") suspend fun deleteHarvest(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlerts(values: List<FarmAlertEntity>): List<Long>
    @Upsert suspend fun upsertAlert(value: FarmAlertEntity)
    @Query("DELETE FROM farm_alerts WHERE id = :id") suspend fun deleteAlert(id: String)
    @Query("UPDATE farm_alerts SET read = :read WHERE id = :id") suspend fun markAlert(id: String, read: Boolean)
    @Upsert suspend fun upsertWeather(values: List<WeatherSnapshotEntity>)
    @Upsert suspend fun upsertMarketPrices(values: List<MarketPriceEntity>)
    @Query("DELETE FROM weather_snapshots WHERE ownerUid = :ownerUid") suspend fun deleteWeather(ownerUid: String)
    @Query("DELETE FROM market_prices WHERE ownerUid = :ownerUid") suspend fun deleteMarketPrices(ownerUid: String)
    @Query("DELETE FROM farm_alerts WHERE ownerUid = :ownerUid") suspend fun deleteAlertsForOwner(ownerUid: String)
    @Query("DELETE FROM crop_inspections WHERE ownerUid = :ownerUid") suspend fun deleteInspectionsForOwner(ownerUid: String)
    @Query("DELETE FROM harvest_records WHERE ownerUid = :ownerUid") suspend fun deleteHarvestsForOwner(ownerUid: String)
    @Query("DELETE FROM ai_conversations WHERE ownerUid = :ownerUid") suspend fun deleteConversationsForOwner(ownerUid: String)

    @Transaction suspend fun saveInspection(value: CropInspectionEntity, symptoms: List<CropSymptomEntity>) {
        upsertInspection(value)
        deleteSymptoms(value.id)
        upsertSymptoms(symptoms)
    }

    @Transaction suspend fun saveHarvest(value: HarvestRecordEntity, expenses: List<ActualExpenseEntity>) {
        upsertHarvest(value)
        deleteExpenses(value.id)
        upsertExpenses(expenses)
    }

    @Transaction suspend fun replaceWeather(ownerUid: String, values: List<WeatherSnapshotEntity>) {
        deleteWeather(ownerUid)
        upsertWeather(values)
    }

    @Transaction suspend fun clearOwner(ownerUid: String) {
        deleteAlertsForOwner(ownerUid)
        deleteInspectionsForOwner(ownerUid)
        deleteHarvestsForOwner(ownerUid)
        deleteConversationsForOwner(ownerUid)
        deleteWeather(ownerUid)
        deleteMarketPrices(ownerUid)
    }
}

internal fun AiConversationEntity.toDomain() = AiConversation(id, title, createdAt, updatedAt)
internal fun AiConversation.toEntity(ownerUid: String = "local") = AiConversationEntity(id, title, createdAt, updatedAt, ownerUid)
internal fun ChatMessageEntity.toDomain() = ChatMessage(id, conversationId, role, text, createdAt, cardTitle, cardLines.split('\n').filter(String::isNotBlank))
internal fun ChatMessage.toEntity(ownerUid: String = "local") = ChatMessageEntity(id, conversationId, role, text, createdAt, cardTitle, cardLines.joinToString("\n"), ownerUid)
internal fun InspectionWithSymptoms.toDomain() = inspection.run { CropInspection(id, plotId, imageUri, cropName, symptoms.mapTo(linkedSetOf(), CropSymptomEntity::code), decode(affectedParts).toSet(), observations, decode(possibleCauses), decode(evidenceNeeded), decode(nextSteps), createdAt, imageModelUsed) }
internal fun CropInspection.toEntity(ownerUid: String = "local") = CropInspectionEntity(id, plotId, imageUri, cropName, affectedParts.joinToString("|"), observations, possibleCauses.joinToString("|"), evidenceNeeded.joinToString("|"), nextSteps.joinToString("|"), createdAt, imageModelUsed, ownerUid)
internal fun CropInspection.symptomEntities(ownerUid: String = "local") = symptoms.map { CropSymptomEntity("$id:$it", id, it, ownerUid) }
internal fun HarvestRecordEntity.toDomain() = HarvestRecord(id, planId, LocalDate.ofEpochDay(actualHarvestEpochDay), quantity, unit, sellingPricePerUnit, actualTotalExpenses, notes, createdAt)
internal fun HarvestRecord.toEntity(ownerUid: String = "local") = HarvestRecordEntity(id, planId, actualHarvestDate.toEpochDay(), quantity, unit, sellingPricePerUnit, actualTotalExpenses, notes, createdAt, ownerUid)
internal fun ActualExpenseEntity.toDomain() = ActualExpense(id, harvestId, label, amount)
internal fun ActualExpense.toEntity(ownerUid: String = "local") = ActualExpenseEntity(id, harvestId, label, amount, ownerUid)
internal fun FarmAlertEntity.toDomain() = FarmAlert(id, sourceKey, title, message, relatedPlanId, relatedPlotId, priority, scheduledAt, read, action)
internal fun FarmAlert.toEntity(ownerUid: String = "local") = FarmAlertEntity(id, sourceKey, title, message, relatedPlanId, relatedPlotId, priority, scheduledAt, read, action, ownerUid)
internal fun WeatherSnapshotEntity.toDomain() = WeatherSnapshot(id, location, observedAt, forecastFor, temperatureC, humidityPercent, windKph, rainMillimetres, condition, source)
internal fun WeatherSnapshot.toEntity(ownerUid: String = "local") = WeatherSnapshotEntity(id, location, observedAt, forecastFor, temperatureC, humidityPercent, windKph, rainMillimetres, condition, source, ownerUid)
internal fun MarketPriceEntity.toDomain() = MarketPriceRecord(id, crop, state, district, market, minPricePerQuintal, maxPricePerQuintal, modalPricePerQuintal, LocalDate.ofEpochDay(dataDateEpochDay), source, savedAt)
internal fun MarketPriceRecord.toEntity(ownerUid: String = "local") = MarketPriceEntity(id, crop, state, district, market, minPricePerQuintal, maxPricePerQuintal, modalPricePerQuintal, dataDate.toEpochDay(), source, savedAt, ownerUid)
private fun decode(value: String) = value.split('|').filter(String::isNotBlank)
