package com.agroai.app.data

import com.agroai.app.data.local.*
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

interface FarmRepository {
    val plots: Flow<List<FarmPlot>>
    val boundaries: Flow<List<FarmBoundary>>
    val plans: Flow<List<CropPlan>>
    val activities: Flow<List<CalendarActivity>>
    suspend fun savePlot(plot: FarmPlot)
    suspend fun deletePlot(id: String)
    suspend fun saveBoundary(boundary: FarmBoundary)
    suspend fun deleteBoundary(plotId: String)
    suspend fun savePlan(plan: CropPlan, activities: List<CropPlanActivity>)
    suspend fun deletePlan(id: String)
    suspend fun saveActivity(activity: CalendarActivity)
    suspend fun deleteActivity(id: String)
    suspend fun upcomingReminders(after: Long): List<CalendarActivity>
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoomFarmRepository(
    private val dao: FarmDao,
    private val accountScope: AccountScope = AccountScope(),
    private val sync: LocalSyncQueue? = null,
) : FarmRepository {
    override val plots = accountScope.owner.flatMapLatest(dao::observePlots).map { rows -> rows.map(FarmPlotEntity::toDomain) }
    override val boundaries = accountScope.owner.flatMapLatest(dao::observeBoundaries).map { rows -> rows.map(FarmBoundaryEntity::toDomain) }
    override val plans = accountScope.owner.flatMapLatest(dao::observePlans).map { rows -> rows.map(CropPlanEntity::toDomain) }
    override val activities = accountScope.owner.flatMapLatest(dao::observeActivities).map { rows -> rows.map(CalendarActivityEntity::toDomain) }

    override suspend fun savePlot(plot: FarmPlot) {
        val owner = accountScope.current
        val entity = plot.toEntity(owner)
        mutate(owner, upsertMutation(CloudCollections.PLOTS, plot.id, CloudPayloads.plot(entity))) { dao.upsertPlot(entity) }
    }

    override suspend fun deletePlot(id: String) {
        val owner = accountScope.current
        if (sync == null) return dao.deletePlotData(id)
        sync.mutate(owner) {
            val boundary = dao.boundaries(owner).filter { it.plotId == id }
            val plans = dao.plans(owner).filter { it.plotId == id }
            val activities = dao.activities(owner).filter { it.plotId == id || it.planId in plans.map(CropPlanEntity::id) }
            val stage3 = sync.database.stage3Dao()
            val harvests = if (plans.isEmpty()) emptyList() else stage3.harvestsForPlans(owner, plans.map(CropPlanEntity::id))
            val expenses = if (harvests.isEmpty()) emptyList() else stage3.expensesForHarvests(owner, harvests.map(HarvestRecordEntity::id))
            val inspections = stage3.inspectionsForPlot(owner, id)
            dao.deletePlotData(id)
            val mutations = buildList {
                add(deleteMutation(CloudCollections.PLOTS, id))
                boundary.forEach { add(deleteMutation(CloudCollections.BOUNDARIES, it.id)) }
                plans.forEach { add(deleteMutation(CloudCollections.PLANS, it.id)) }
                activities.forEach { add(deleteMutation(CloudCollections.ACTIVITIES, it.id)) }
                harvests.forEach { add(deleteMutation(CloudCollections.HARVESTS, it.id)) }
                expenses.forEach { add(deleteMutation(CloudCollections.EXPENSES, it.id)) }
                inspections.forEach { value -> add(upsertMutation(CloudCollections.INSPECTIONS, value.inspection.id, CloudPayloads.inspection(value.copy(inspection = value.inspection.copy(plotId = null))))) }
            }
            Unit to mutations
        }
    }

    override suspend fun saveBoundary(boundary: FarmBoundary) {
        val owner = accountScope.current
        val entity = boundary.toEntity(owner)
        mutate(owner, upsertMutation(CloudCollections.BOUNDARIES, boundary.id, CloudPayloads.boundary(entity))) { dao.upsertBoundary(entity) }
    }

    override suspend fun deleteBoundary(plotId: String) {
        val owner = accountScope.current
        val values = dao.boundaries(owner).filter { it.plotId == plotId }
        mutate(owner, values.map { deleteMutation(CloudCollections.BOUNDARIES, it.id) }) { dao.deleteBoundary(plotId) }
    }

    override suspend fun savePlan(plan: CropPlan, activities: List<CropPlanActivity>) {
        val owner = accountScope.current
        val planEntity = plan.toEntity(owner)
        val activityEntities = activities.map { it.toEntity(owner) }
        val removed = dao.activities(owner).filter { it.planId == plan.id && it.id !in activityEntities.map(CalendarActivityEntity::id) }
        val mutations = listOf(upsertMutation(CloudCollections.PLANS, plan.id, CloudPayloads.plan(planEntity))) +
            activityEntities.map { upsertMutation(CloudCollections.ACTIVITIES, it.id, CloudPayloads.activity(it)) } +
            removed.map { deleteMutation(CloudCollections.ACTIVITIES, it.id) }
        mutate(owner, mutations) { dao.savePlanWithActivities(planEntity, activityEntities) }
    }

    override suspend fun deletePlan(id: String) {
        val owner = accountScope.current
        val activities = dao.activities(owner).filter { it.planId == id }
        val stage3 = sync?.database?.stage3Dao()
        val harvests = stage3?.harvestsForPlans(owner, listOf(id)).orEmpty()
        val expenses = if (harvests.isEmpty()) emptyList() else stage3?.expensesForHarvests(owner, harvests.map(HarvestRecordEntity::id)).orEmpty()
        val mutations = listOf(deleteMutation(CloudCollections.PLANS, id)) +
            activities.map { deleteMutation(CloudCollections.ACTIVITIES, it.id) } +
            harvests.map { deleteMutation(CloudCollections.HARVESTS, it.id) } +
            expenses.map { deleteMutation(CloudCollections.EXPENSES, it.id) }
        mutate(owner, mutations) { dao.deleteActivitiesForPlan(id); dao.deletePlan(id) }
    }

    override suspend fun saveActivity(activity: CalendarActivity) {
        val owner = accountScope.current
        val entity = activity.toEntity(owner)
        mutate(owner, upsertMutation(CloudCollections.ACTIVITIES, activity.id, CloudPayloads.activity(entity))) { dao.upsertActivity(entity) }
    }

    override suspend fun deleteActivity(id: String) {
        val owner = accountScope.current
        mutate(owner, deleteMutation(CloudCollections.ACTIVITIES, id)) { dao.deleteActivity(id) }
    }

    override suspend fun upcomingReminders(after: Long) = dao.upcomingReminders(accountScope.current, after).map(CalendarActivityEntity::toDomain)

    private suspend fun mutate(owner: String, mutation: CloudMutation, write: suspend () -> Unit) = mutate(owner, listOf(mutation), write)
    private suspend fun mutate(owner: String, mutations: List<CloudMutation>, write: suspend () -> Unit) {
        sync?.mutate(owner) { write(); Unit to mutations } ?: write()
    }
}
