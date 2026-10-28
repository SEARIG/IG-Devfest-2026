package com.agroai.app.data

import com.agroai.app.data.local.*
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

interface Stage3Repository : ConversationRepository, WeatherProvider, MarketPriceProvider {
    val inspections: Flow<List<CropInspection>>
    val harvests: Flow<List<HarvestRecord>>
    val expenses: Flow<List<ActualExpense>>
    val alerts: Flow<List<FarmAlert>>
    suspend fun saveInspection(value: CropInspection)
    suspend fun deleteInspection(id: String)
    suspend fun saveHarvest(value: HarvestRecord, expenses: List<ActualExpense>)
    suspend fun deleteHarvest(id: String)
    suspend fun saveAlerts(values: List<FarmAlert>): List<FarmAlert>
    suspend fun markAlert(id: String, read: Boolean)
    suspend fun saveWeather(values: List<WeatherSnapshot>)
    suspend fun saveMarketPrices(values: List<MarketPriceRecord>)
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomStage3Repository(
    private val dao: Stage3Dao,
    private val accountScope: AccountScope = AccountScope(),
    private val sync: LocalSyncQueue? = null,
) : Stage3Repository {
    override val conversations = accountScope.owner.flatMapLatest(dao::observeConversations).map { rows -> rows.map(AiConversationEntity::toDomain) }
    override fun messages(conversationId: String) = accountScope.owner.flatMapLatest { dao.observeMessages(it, conversationId) }.map { rows -> rows.map(ChatMessageEntity::toDomain) }
    override val inspections = accountScope.owner.flatMapLatest(dao::observeInspections).map { rows -> rows.map(InspectionWithSymptoms::toDomain) }
    override val harvests = accountScope.owner.flatMapLatest(dao::observeHarvests).map { rows -> rows.map(HarvestRecordEntity::toDomain) }
    override val expenses = accountScope.owner.flatMapLatest(dao::observeExpenses).map { rows -> rows.map(ActualExpenseEntity::toDomain) }
    override val alerts = accountScope.owner.flatMapLatest(dao::observeAlerts).map { rows -> rows.map(FarmAlertEntity::toDomain) }
    override val snapshots = accountScope.owner.flatMapLatest(dao::observeWeather).map { rows -> rows.map(WeatherSnapshotEntity::toDomain) }
    override val prices = accountScope.owner.flatMapLatest(dao::observeMarketPrices).map { rows -> rows.map(MarketPriceEntity::toDomain) }

    override suspend fun saveConversation(conversation: AiConversation) {
        val owner = accountScope.current
        val entity = conversation.toEntity(owner)
        mutate(owner, upsertMutation(CloudCollections.CONVERSATIONS, conversation.id, CloudPayloads.conversation(entity))) { dao.upsertConversation(entity) }
    }

    override suspend fun saveMessage(message: ChatMessage) {
        val owner = accountScope.current
        val entity = message.toEntity(owner)
        mutate(owner, upsertMutation(CloudCollections.MESSAGES, message.id, CloudPayloads.message(entity))) { dao.upsertMessage(entity) }
    }

    override suspend fun saveInspection(value: CropInspection) {
        val owner = accountScope.current
        val entity = value.toEntity(owner)
        val symptoms = value.symptomEntities(owner)
        val payload = CloudPayloads.inspection(InspectionWithSymptoms(entity, symptoms))
        mutate(owner, upsertMutation(CloudCollections.INSPECTIONS, value.id, payload)) { dao.saveInspection(entity, symptoms) }
    }

    override suspend fun deleteInspection(id: String) {
        val owner = accountScope.current
        mutate(owner, deleteMutation(CloudCollections.INSPECTIONS, id)) { dao.deleteInspection(id) }
    }

    override suspend fun saveHarvest(value: HarvestRecord, expenses: List<ActualExpense>) {
        val owner = accountScope.current
        val harvest = value.toEntity(owner)
        val expenseEntities = expenses.map { it.toEntity(owner) }
        val removed = dao.expensesForHarvest(owner, value.id).filter { old -> old.id !in expenseEntities.map(ActualExpenseEntity::id) }
        val mutations = listOf(upsertMutation(CloudCollections.HARVESTS, value.id, CloudPayloads.harvest(harvest))) +
            expenseEntities.map { upsertMutation(CloudCollections.EXPENSES, it.id, CloudPayloads.expense(it)) } +
            removed.map { deleteMutation(CloudCollections.EXPENSES, it.id) }
        mutate(owner, mutations) { dao.saveHarvest(harvest, expenseEntities) }
    }

    override suspend fun deleteHarvest(id: String) {
        val owner = accountScope.current
        val expenses = dao.expensesForHarvest(owner, id)
        val mutations = listOf(deleteMutation(CloudCollections.HARVESTS, id)) + expenses.map { deleteMutation(CloudCollections.EXPENSES, it.id) }
        mutate(owner, mutations) { dao.deleteHarvest(id) }
    }
    override suspend fun saveAlerts(values: List<FarmAlert>): List<FarmAlert> {
        val owner = accountScope.current
        if (sync == null) {
            val inserted = dao.insertAlerts(values.map { it.toEntity(owner) })
            return values.filterIndexed { index, _ -> inserted[index] != -1L }
        }
        return sync.mutate(owner) {
            val entities = values.map { it.toEntity(owner) }
            val inserted = dao.insertAlerts(entities)
            val saved = values.filterIndexed { index, _ -> inserted[index] != -1L }
            val mutations = entities.filterIndexed { index, _ -> inserted[index] != -1L }
                .map { upsertMutation(CloudCollections.ALERTS, it.id, CloudPayloads.alert(it)) }
            saved to mutations
        }
    }

    override suspend fun markAlert(id: String, read: Boolean) {
        val owner = accountScope.current
        if (sync == null) return dao.markAlert(id, read)
        sync.mutate(owner) {
            dao.markAlert(id, read)
            val alert = dao.alert(owner, id)
            Unit to alert?.let { listOf(upsertMutation(CloudCollections.ALERTS, id, CloudPayloads.alert(it))) }.orEmpty()
        }
    }

    override suspend fun saveWeather(values: List<WeatherSnapshot>) {
        val owner = accountScope.current
        dao.replaceWeather(owner, values.map { it.toEntity(owner) })
    }

    override suspend fun saveMarketPrices(values: List<MarketPriceRecord>) {
        val owner = accountScope.current
        dao.upsertMarketPrices(values.map { it.toEntity(owner) })
    }

    private suspend fun mutate(owner: String, mutation: CloudMutation, write: suspend () -> Unit) = mutate(owner, listOf(mutation), write)
    private suspend fun mutate(owner: String, mutations: List<CloudMutation>, write: suspend () -> Unit) {
        sync?.mutate(owner) { write(); Unit to mutations } ?: write()
    }
}

class RepositoryFarmerContextProvider(
    private val farms: FarmRepository,
    private val stage3: Stage3Repository,
) : FarmerContextProvider {
    override suspend fun current() = FarmerContext(
        plots = farms.plots.first(),
        plans = farms.plans.first(),
        activities = farms.activities.first(),
        harvests = stage3.harvests.first(),
    )
}
