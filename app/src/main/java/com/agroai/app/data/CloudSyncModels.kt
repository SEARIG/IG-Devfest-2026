package com.agroai.app.data

import com.agroai.app.data.local.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val LOCAL_OWNER = "local"
const val DEMO_OWNER = "demo"

class AccountScope {
    private val mutableOwner = MutableStateFlow(LOCAL_OWNER)
    val owner: StateFlow<String> = mutableOwner.asStateFlow()
    val current: String get() = mutableOwner.value
    fun useAuthenticated(uid: String) { require(uid.isNotBlank()); mutableOwner.value = uid }
    fun useLocal() { mutableOwner.value = LOCAL_OWNER }
    fun useDemo() { mutableOwner.value = DEMO_OWNER }
}

enum class CloudSyncStatus { Synced, Pending, Offline, Failed, Conflict, AuthenticationRequired }

data class CloudSyncSummary(
    val status: CloudSyncStatus,
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val lastSyncAt: Long? = null,
    val error: String? = null,
)

object CloudCollections {
    const val PLOTS = "plots"
    const val BOUNDARIES = "boundaries"
    const val PLANS = "cropPlans"
    const val ACTIVITIES = "activities"
    const val CONVERSATIONS = "conversations"
    const val MESSAGES = "chatMessages"
    const val INSPECTIONS = "inspections"
    const val HARVESTS = "harvests"
    const val EXPENSES = "expenses"
    const val ALERTS = "alerts"
    val all = listOf(PLOTS, BOUNDARIES, PLANS, ACTIVITIES, CONVERSATIONS, MESSAGES, INSPECTIONS, HARVESTS, EXPENSES, ALERTS)
}

object CloudPayloadCodec {
    fun encode(values: Map<String, Any?>): String = values.toSortedMap().entries.joinToString("\n") { (key, value) ->
        val (type, raw) = when (value) {
            null -> "n" to ""
            is Boolean -> "b" to value.toString()
            is Byte, is Short, is Int, is Long -> "l" to (value as Number).toLong().toString()
            is Float, is Double -> "d" to (value as Number).toDouble().toString()
            is List<*> -> "a" to value.joinToString(",") { encodeText(it?.toString().orEmpty()) }
            else -> "s" to encodeText(value.toString())
        }
        "$key|$type|$raw"
    }

    fun decode(value: String): Map<String, Any?> = value.lineSequence().filter(String::isNotBlank).associate { line ->
        val parts = line.split('|', limit = 3)
        require(parts.size == 3) { "Invalid sync payload" }
        parts[0] to when (parts[1]) {
            "n" -> null
            "b" -> parts[2].toBooleanStrict()
            "l" -> parts[2].toLong()
            "d" -> parts[2].toDouble()
            "a" -> if (parts[2].isBlank()) emptyList<String>() else parts[2].split(',').map(::decodeText)
            "s" -> decodeText(parts[2])
            else -> error("Invalid sync payload type")
        }
    }

    private fun encodeText(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun decodeText(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

internal object CloudPayloads {
    fun plot(value: FarmPlotEntity) = mapOf(
        "name" to value.name, "area" to value.area, "areaUnit" to value.areaUnit,
        "soilType" to value.soilType, "irrigationMethod" to value.irrigationMethod,
        "cropHistory" to value.cropHistory, "clientUpdatedAt" to value.updatedAt,
    )

    fun boundary(value: FarmBoundaryEntity) = mapOf(
        "plotId" to value.plotId, "encodedPoints" to value.encodedPoints,
        "areaSquareMeters" to value.areaSquareMeters, "accuracyMeters" to value.accuracyMeters,
        "clientUpdatedAt" to value.updatedAt,
    )

    fun plan(value: CropPlanEntity) = mapOf(
        "plotId" to value.plotId, "cropId" to value.cropId, "sowingEpochDay" to value.sowingEpochDay,
        "areaHectares" to value.areaHectares, "irrigationMethod" to value.irrigationMethod,
        "seedVariety" to value.seedVariety, "status" to value.status,
        "seedQuantityKgLow" to value.seedQuantityKgLow, "seedQuantityKgHigh" to value.seedQuantityKgHigh,
        "harvestStartEpochDay" to value.harvestStartEpochDay, "harvestEndEpochDay" to value.harvestEndEpochDay,
        "clientUpdatedAt" to value.updatedAt,
    )

    fun activity(value: CalendarActivityEntity) = mapOf(
        "planId" to value.planId, "plotId" to value.plotId, "type" to value.type,
        "title" to value.title, "scheduledAt" to value.scheduledAt, "notes" to value.notes,
        "completed" to value.completed, "reminderEnabled" to value.reminderEnabled,
    )

    fun conversation(value: AiConversationEntity) = mapOf(
        "title" to value.title, "createdAt" to value.createdAt, "clientUpdatedAt" to value.updatedAt,
    )

    fun message(value: ChatMessageEntity) = mapOf(
        "conversationId" to value.conversationId, "role" to value.role, "text" to value.text,
        "createdAt" to value.createdAt, "cardTitle" to value.cardTitle, "cardLines" to value.cardLines,
    )

    fun inspection(value: InspectionWithSymptoms) = value.inspection.run { mapOf(
        "plotId" to plotId, "cropName" to cropName, "symptoms" to value.symptoms.map(CropSymptomEntity::code),
        "affectedParts" to affectedParts.split('|').filter(String::isNotBlank), "observations" to observations,
        "possibleCauses" to possibleCauses.split('|').filter(String::isNotBlank),
        "evidenceNeeded" to evidenceNeeded.split('|').filter(String::isNotBlank),
        "nextSteps" to nextSteps.split('|').filter(String::isNotBlank), "createdAt" to createdAt,
        "imageModelUsed" to imageModelUsed,
    ) }

    fun harvest(value: HarvestRecordEntity) = mapOf(
        "planId" to value.planId, "actualHarvestEpochDay" to value.actualHarvestEpochDay,
        "quantity" to value.quantity, "unit" to value.unit, "sellingPricePerUnit" to value.sellingPricePerUnit,
        "actualTotalExpenses" to value.actualTotalExpenses, "notes" to value.notes, "createdAt" to value.createdAt,
    )

    fun expense(value: ActualExpenseEntity) = mapOf(
        "harvestId" to value.harvestId, "label" to value.label, "amount" to value.amount,
    )

    fun alert(value: FarmAlertEntity) = mapOf(
        "sourceKey" to value.sourceKey, "title" to value.title, "message" to value.message,
        "relatedPlanId" to value.relatedPlanId, "relatedPlotId" to value.relatedPlotId,
        "priority" to value.priority, "scheduledAt" to value.scheduledAt, "read" to value.read, "action" to value.action,
    )
}

internal fun Map<String, Any?>.text(key: String) = this[key] as? String ?: ""
internal fun Map<String, Any?>.nullableText(key: String) = this[key] as? String
internal fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong() ?: 0L
internal fun Map<String, Any?>.double(key: String) = (this[key] as? Number)?.toDouble() ?: 0.0
internal fun Map<String, Any?>.nullableDouble(key: String) = (this[key] as? Number)?.toDouble()
internal fun Map<String, Any?>.boolean(key: String) = this[key] as? Boolean ?: false
internal fun Map<String, Any?>.texts(key: String) = (this[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
