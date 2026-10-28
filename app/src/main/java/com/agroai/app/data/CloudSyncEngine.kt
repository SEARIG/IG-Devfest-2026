package com.agroai.app.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agroai.app.AgroAiApplication
import com.agroai.app.data.local.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class CloudSyncManager(
    private val context: Context,
    private val database: AgroDatabase,
) {
    fun schedule(ownerUid: String) {
        if (ownerUid in setOf(LOCAL_OWNER, DEMO_OWNER)) return
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(Data.Builder().putString(CloudSyncWorker.OWNER_UID, ownerUid).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("agro-cloud-sync-$ownerUid", ExistingWorkPolicy.KEEP, request)
    }

    fun observe(ownerUid: String): Flow<CloudSyncSummary> = combine(
        database.syncDao().observeRecords(ownerUid),
        database.syncDao().observeStates(ownerUid),
    ) { records, states ->
        val pending = records.count { it.state == "pending" }
        val conflicts = records.count { it.state == "conflict" }
        val failure = records.firstOrNull { it.state == "failed" }?.error ?: states.firstNotNullOfOrNull(SyncStateEntity::lastError)
        CloudSyncSummary(
            status = when {
                conflicts > 0 -> CloudSyncStatus.Conflict
                failure == "offline" -> CloudSyncStatus.Offline
                failure != null -> CloudSyncStatus.Failed
                pending > 0 -> CloudSyncStatus.Pending
                else -> CloudSyncStatus.Synced
            },
            pendingCount = pending,
            conflictCount = conflicts,
            lastSyncAt = states.mapNotNull(SyncStateEntity::lastSyncAt).maxOrNull(),
            error = failure,
        )
    }

    suspend fun retry(ownerUid: String) {
        database.syncDao().retryFailed(ownerUid)
        schedule(ownerUid)
    }

    suspend fun keepLocalConflicts(ownerUid: String) {
        val conflicts = database.syncDao().all(ownerUid).filter { it.state == "conflict" }
        database.withTransaction {
            conflicts.forEach { record ->
                val remoteRevision = record.conflictRevision ?: record.remoteRevision
                database.syncDao().upsert(record.copy(
                    state = "pending", remoteRevision = remoteRevision, localRevision = remoteRevision + 1,
                    conflictPayload = null, conflictRevision = null, conflictOperation = null, error = null,
                ))
            }
        }
        schedule(ownerUid)
    }

    suspend fun useCloudConflicts(ownerUid: String, applier: RemoteRecordApplier) {
        database.syncDao().all(ownerUid)
            .filter { it.state == "conflict" }
            .sortedBy { CloudCollections.all.indexOf(it.collection) }
            .forEach { record ->
            val payload = record.conflictPayload ?: return@forEach
            val revision = record.conflictRevision ?: return@forEach
            database.withTransaction {
                applier.apply(ownerUid, record.collection, record.recordId, CloudPayloadCodec.decode(payload), record.conflictOperation == "delete")
                database.syncDao().upsert(record.copy(
                    operation = record.conflictOperation ?: "upsert", payload = payload,
                    localRevision = revision, remoteRevision = revision, state = "synced",
                    conflictPayload = null, conflictRevision = null, conflictOperation = null, error = null,
                ))
            }
        }
    }
}

class CloudSyncEngine(
    private val database: AgroDatabase,
    private val firestore: FirebaseFirestore,
    private val applier: RemoteRecordApplier,
) {
    suspend fun sync(ownerUid: String) {
        require(ownerUid !in setOf(LOCAL_OWNER, DEMO_OWNER))
        push(ownerUid)
        pull(ownerUid)
    }

    private suspend fun push(ownerUid: String) {
        while (true) {
            val batch = database.syncDao().pending(ownerUid)
            if (batch.isEmpty()) break
            batch.forEach { record ->
            val ref = firestore.collection("farmers").document(ownerUid).collection(record.collection).document(record.recordId)
            try {
                val conflict = firestore.runTransaction<SyncConflict?> { transaction ->
                    val remote = transaction.get(ref)
                    val remoteRevision = remote.getLong("revision") ?: 0L
                    if (remoteRevision > record.remoteRevision) {
                        SyncConflict(
                            payload = CloudPayloadCodec.encode(remote.get("payload").stringMap()),
                            revision = remoteRevision,
                            operation = if (remote.getBoolean("deleted") == true) "delete" else "upsert",
                        )
                    } else {
                        transaction.set(ref, mapOf(
                            "ownerUid" to ownerUid,
                            "recordId" to record.recordId,
                            "revision" to record.localRevision,
                            "deleted" to (record.operation == "delete"),
                            "payload" to if (record.operation == "delete") emptyMap<String, Any?>() else CloudPayloadCodec.decode(record.payload),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ))
                        null
                    }
                }.await()
                if (conflict == null) {
                    database.syncDao().upsert(record.copy(
                        remoteRevision = record.localRevision, state = "synced", lastAttemptAt = System.currentTimeMillis(), error = null,
                    ))
                } else {
                    database.syncDao().upsert(record.copy(
                        state = "conflict", lastAttemptAt = System.currentTimeMillis(), error = "conflict",
                        conflictPayload = conflict.payload, conflictRevision = conflict.revision, conflictOperation = conflict.operation,
                    ))
                }
            } catch (error: Exception) {
                database.syncDao().upsert(record.copy(state = "failed", lastAttemptAt = System.currentTimeMillis(), error = syncError(error)))
                throw error
            }
        }
        }
    }

    private suspend fun pull(ownerUid: String) {
        CloudCollections.all.forEach { collection ->
            val dao = database.syncDao()
            val state = dao.state(ownerUid, collection) ?: SyncStateEntity(ownerUid, collection)
            try {
                val cursor = Timestamp(state.lastServerSeconds, state.lastServerNanos)
                val documents = firestore.collection("farmers").document(ownerUid).collection(collection)
                    .whereGreaterThan("updatedAt", cursor).orderBy("updatedAt").get(Source.SERVER).await().documents
                var latest = cursor
                documents.forEach { document ->
                    if (document.getString("ownerUid") != ownerUid || document.getString("recordId") != document.id) return@forEach
                    val revision = document.getLong("revision") ?: return@forEach
                    val deleted = document.getBoolean("deleted") == true
                    val payloadMap = document.get("payload").stringMap()
                    val payload = CloudPayloadCodec.encode(payloadMap)
                    val local = dao.record(ownerUid, collection, document.id)
                    if (local != null && local.state in setOf("pending", "failed", "conflict") && revision > local.remoteRevision) {
                        dao.upsert(local.copy(
                            state = "conflict", error = "conflict", conflictPayload = payload,
                            conflictRevision = revision, conflictOperation = if (deleted) "delete" else "upsert",
                        ))
                    } else if (local == null || revision > local.remoteRevision) {
                        database.withTransaction {
                            applier.apply(ownerUid, collection, document.id, payloadMap, deleted)
                            dao.upsert(SyncRecordEntity(
                                ownerUid, collection, document.id, if (deleted) "delete" else "upsert", payload,
                                revision, revision, "synced", System.currentTimeMillis(),
                            ))
                        }
                    }
                    val timestamp = document.getTimestamp("updatedAt")
                    if (timestamp != null && timestamp > latest) latest = timestamp
                }
                dao.upsertState(state.copy(
                    lastServerSeconds = latest.seconds, lastServerNanos = latest.nanoseconds,
                    lastSyncAt = System.currentTimeMillis(), lastError = null,
                ))
            } catch (error: Exception) {
                dao.upsertState(state.copy(lastError = syncError(error)))
                throw error
            }
        }
    }
}

class RemoteRecordApplier(private val database: AgroDatabase) {
    suspend fun apply(ownerUid: String, collection: String, id: String, value: Map<String, Any?>, deleted: Boolean) {
        val farm = database.farmDao()
        val stage3 = database.stage3Dao()
        if (deleted) {
            when (collection) {
                CloudCollections.PLOTS -> farm.deletePlotData(id)
                CloudCollections.BOUNDARIES -> farm.deleteBoundaryById(id)
                CloudCollections.PLANS -> { farm.deleteActivitiesForPlan(id); farm.deletePlan(id) }
                CloudCollections.ACTIVITIES -> farm.deleteActivity(id)
                CloudCollections.CONVERSATIONS -> stage3.deleteConversation(id)
                CloudCollections.MESSAGES -> stage3.deleteMessage(id)
                CloudCollections.INSPECTIONS -> stage3.deleteInspection(id)
                CloudCollections.HARVESTS -> stage3.deleteHarvest(id)
                CloudCollections.EXPENSES -> stage3.deleteExpense(id)
                CloudCollections.ALERTS -> stage3.deleteAlert(id)
            }
            return
        }
        when (collection) {
            CloudCollections.PLOTS -> farm.upsertPlot(FarmPlotEntity(id, value.text("name"), value.double("area"), value.text("areaUnit"), value.text("soilType"), value.text("irrigationMethod"), value.text("cropHistory"), value.long("clientUpdatedAt"), ownerUid))
            CloudCollections.BOUNDARIES -> farm.upsertBoundary(FarmBoundaryEntity(id, value.text("plotId"), value.text("encodedPoints"), value.double("areaSquareMeters"), value.nullableDouble("accuracyMeters")?.toFloat(), value.long("clientUpdatedAt"), ownerUid))
            CloudCollections.PLANS -> farm.upsertPlan(CropPlanEntity(id, value.text("plotId"), value.text("cropId"), value.long("sowingEpochDay"), value.double("areaHectares"), value.text("irrigationMethod"), value.text("seedVariety"), value.text("status"), value.double("seedQuantityKgLow"), value.double("seedQuantityKgHigh"), value.long("harvestStartEpochDay"), value.long("harvestEndEpochDay"), value.long("clientUpdatedAt"), ownerUid))
            CloudCollections.ACTIVITIES -> farm.upsertActivity(CalendarActivityEntity(id, value.nullableText("planId"), value.nullableText("plotId"), value.text("type"), value.text("title"), value.long("scheduledAt"), value.text("notes"), value.boolean("completed"), value.boolean("reminderEnabled"), ownerUid))
            CloudCollections.CONVERSATIONS -> stage3.upsertConversation(AiConversationEntity(id, value.text("title"), value.long("createdAt"), value.long("clientUpdatedAt"), ownerUid))
            CloudCollections.MESSAGES -> stage3.upsertMessage(ChatMessageEntity(id, value.text("conversationId"), value.text("role"), value.text("text"), value.long("createdAt"), value.nullableText("cardTitle"), value.text("cardLines"), ownerUid))
            CloudCollections.INSPECTIONS -> {
                val existingImage = stage3.inspection(ownerUid, id)?.inspection?.imageUri
                val inspection = CropInspectionEntity(id, value.nullableText("plotId"), existingImage, value.text("cropName"), value.texts("affectedParts").joinToString("|"), value.text("observations"), value.texts("possibleCauses").joinToString("|"), value.texts("evidenceNeeded").joinToString("|"), value.texts("nextSteps").joinToString("|"), value.long("createdAt"), value.boolean("imageModelUsed"), ownerUid)
                stage3.saveInspection(inspection, value.texts("symptoms").map { CropSymptomEntity("$id:$it", id, it, ownerUid) })
            }
            CloudCollections.HARVESTS -> stage3.upsertHarvest(HarvestRecordEntity(id, value.text("planId"), value.long("actualHarvestEpochDay"), value.double("quantity"), value.text("unit"), value.nullableDouble("sellingPricePerUnit"), value.double("actualTotalExpenses"), value.text("notes"), value.long("createdAt"), ownerUid))
            CloudCollections.EXPENSES -> stage3.upsertExpenses(listOf(ActualExpenseEntity(id, value.text("harvestId"), value.text("label"), value.double("amount"), ownerUid)))
            CloudCollections.ALERTS -> stage3.upsertAlert(FarmAlertEntity(id, value.text("sourceKey"), value.text("title"), value.text("message"), value.nullableText("relatedPlanId"), value.nullableText("relatedPlotId"), value.text("priority"), value.long("scheduledAt"), value.boolean("read"), value.text("action"), ownerUid))
            else -> error("Unsupported cloud collection")
        }
    }
}

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val ownerUid = inputData.getString(OWNER_UID) ?: return Result.failure()
        val container = (applicationContext as AgroAiApplication).container
        if (container.auth?.currentUid != ownerUid) return Result.failure()
        return runCatching { requireNotNull(container.cloudSyncEngine).sync(ownerUid) }
            .fold(onSuccess = { Result.success() }, onFailure = { if (runAttemptCount < 5) Result.retry() else Result.failure() })
    }

    companion object { const val OWNER_UID = "owner_uid" }
}

private data class SyncConflict(val payload: String, val revision: Long, val operation: String)

private fun Any?.stringMap(): Map<String, Any?> = (this as? Map<*, *>)?.entries?.mapNotNull { (key, value) ->
    (key as? String)?.let { it to value }
}?.toMap().orEmpty()

private fun syncError(error: Throwable): String {
    val message = error.message.orEmpty().lowercase()
    return if (message.contains("network") || message.contains("unavailable") || message.contains("offline")) "offline" else error.javaClass.simpleName
}
