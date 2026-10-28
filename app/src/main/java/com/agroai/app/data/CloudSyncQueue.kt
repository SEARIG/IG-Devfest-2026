package com.agroai.app.data

import androidx.room.withTransaction
import com.agroai.app.data.local.AgroDatabase
import com.agroai.app.data.local.SyncRecordEntity

data class CloudMutation(
    val collection: String,
    val recordId: String,
    val operation: String,
    val payload: Map<String, Any?> = emptyMap(),
)

class LocalSyncQueue(
    internal val database: AgroDatabase,
    private val schedule: (String) -> Unit,
) {
    suspend fun hasLocalData(): Boolean = database.syncDao().run {
        plotCount(LOCAL_OWNER) + conversationCount(LOCAL_OWNER) + inspectionCount(LOCAL_OWNER) + harvestCount(LOCAL_OWNER) > 0
    }

    suspend fun linkLocalData(newOwnerUid: String) {
        require(newOwnerUid !in setOf(LOCAL_OWNER, DEMO_OWNER))
        var queued = false
        database.withTransaction {
            val farm = database.farmDao()
            val stage3 = database.stage3Dao()
            val plots = farm.plots(LOCAL_OWNER)
            val boundaries = farm.boundaries(LOCAL_OWNER)
            val plans = farm.plans(LOCAL_OWNER)
            val activities = farm.activities(LOCAL_OWNER)
            val conversations = stage3.conversations(LOCAL_OWNER)
            val messages = stage3.messages(LOCAL_OWNER)
            val inspections = stage3.inspections(LOCAL_OWNER)
            val harvests = stage3.harvests(LOCAL_OWNER)
            val expenses = stage3.expenses(LOCAL_OWNER)
            val alerts = stage3.alerts(LOCAL_OWNER)
            val mutations = buildList {
                plots.forEach { add(upsertMutation(CloudCollections.PLOTS, it.id, CloudPayloads.plot(it))) }
                boundaries.forEach { add(upsertMutation(CloudCollections.BOUNDARIES, it.id, CloudPayloads.boundary(it))) }
                plans.forEach { add(upsertMutation(CloudCollections.PLANS, it.id, CloudPayloads.plan(it))) }
                activities.forEach { add(upsertMutation(CloudCollections.ACTIVITIES, it.id, CloudPayloads.activity(it))) }
                conversations.forEach { add(upsertMutation(CloudCollections.CONVERSATIONS, it.id, CloudPayloads.conversation(it))) }
                messages.forEach { add(upsertMutation(CloudCollections.MESSAGES, it.id, CloudPayloads.message(it))) }
                inspections.forEach { add(upsertMutation(CloudCollections.INSPECTIONS, it.inspection.id, CloudPayloads.inspection(it))) }
                harvests.forEach { add(upsertMutation(CloudCollections.HARVESTS, it.id, CloudPayloads.harvest(it))) }
                expenses.forEach { add(upsertMutation(CloudCollections.EXPENSES, it.id, CloudPayloads.expense(it))) }
                alerts.forEach { add(upsertMutation(CloudCollections.ALERTS, it.id, CloudPayloads.alert(it))) }
            }
            database.syncDao().run {
                movePlots(LOCAL_OWNER, newOwnerUid); moveBoundaries(LOCAL_OWNER, newOwnerUid)
                movePlans(LOCAL_OWNER, newOwnerUid); moveActivities(LOCAL_OWNER, newOwnerUid)
                moveConversations(LOCAL_OWNER, newOwnerUid); moveMessages(LOCAL_OWNER, newOwnerUid)
                moveInspections(LOCAL_OWNER, newOwnerUid); moveSymptoms(LOCAL_OWNER, newOwnerUid)
                moveHarvests(LOCAL_OWNER, newOwnerUid); moveExpenses(LOCAL_OWNER, newOwnerUid)
                moveAlerts(LOCAL_OWNER, newOwnerUid); moveWeather(LOCAL_OWNER, newOwnerUid); moveMarket(LOCAL_OWNER, newOwnerUid)
            }
            mutations.forEach { queue(newOwnerUid, it) }
            queued = mutations.isNotEmpty()
        }
        if (queued) schedule(newOwnerUid)
    }

    suspend fun <T> mutate(ownerUid: String, block: suspend () -> Pair<T, List<CloudMutation>>): T {
        var queued = false
        val result = database.withTransaction {
            val (value, mutations) = block()
            if (ownerUid !in setOf(LOCAL_OWNER, DEMO_OWNER)) {
                mutations.forEach { mutation -> queue(ownerUid, mutation) }
                queued = mutations.isNotEmpty()
            }
            value
        }
        if (queued) schedule(ownerUid)
        return result
    }

    private suspend fun queue(ownerUid: String, mutation: CloudMutation) {
        val dao = database.syncDao()
        val current = dao.record(ownerUid, mutation.collection, mutation.recordId)
        val nextRevision = (current?.remoteRevision ?: 0) + 1
        dao.upsert(
            SyncRecordEntity(
                ownerUid = ownerUid,
                collection = mutation.collection,
                recordId = mutation.recordId,
                operation = mutation.operation,
                payload = CloudPayloadCodec.encode(mutation.payload),
                localRevision = nextRevision,
                remoteRevision = current?.remoteRevision ?: 0,
                state = "pending",
                updatedAt = System.currentTimeMillis(),
            )
        )
    }
}

internal fun upsertMutation(collection: String, id: String, payload: Map<String, Any?>) = CloudMutation(collection, id, "upsert", payload)
internal fun deleteMutation(collection: String, id: String) = CloudMutation(collection, id, "delete")
