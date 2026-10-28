package com.agroai.app.data

import androidx.room.withTransaction
import com.agroai.app.data.local.AgroDatabase
import com.agroai.app.data.model.FarmerProfile
import com.agroai.app.domain.*
import java.time.LocalDate
import java.time.ZoneId

class DemoDataManager(
    private val database: AgroDatabase,
    private val accountScope: AccountScope,
    private val farms: FarmRepository,
    private val stage3: Stage3Repository,
) {
    val profile = FarmerProfile(
        uid = DEMO_OWNER, name = "Asha Demo Farmer", phoneNumber = "", preferredLanguage = "en",
        state = "Rajasthan", district = "Jaipur", village = "Fictional DevFest Village",
        area = 2.0, areaUnit = "acre", soilType = "loamy", irrigationType = "drip",
        currentCrops = listOf("Wheat"), goals = listOf("Demo only"), experienceLevel = "intermediate", onboardingCompleted = true,
    )

    suspend fun reset() {
        accountScope.useDemo()
        database.withTransaction {
            database.stage3Dao().clearOwner(DEMO_OWNER)
            database.farmDao().clearOwner(DEMO_OWNER)
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val plot = FarmPlot("demo-plot", "Demo North Plot", 2.0, "acre", "loamy", "drip", "Fictional demo data", now)
        val plan = CropPlan("demo-plan", plot.id, "wheat", today.minusDays(20), 0.81, "drip", "Demo variety", "active", 81.0, 101.0, today.plusDays(85), today.plusDays(100), now)
        val pastPlan = CropPlan("demo-plan-past", plot.id, "wheat", today.minusDays(140), 0.81, "drip", "Demo variety", "completed", 81.0, 101.0, today.minusDays(35), today.minusDays(25), now)
        val activity = CropPlanActivity("demo-activity", plan.id, plot.id, "irrigation", "Demo soil-moisture check", today.plusDays(1).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Fictional demo task")
        farms.savePlot(plot)
        farms.savePlan(plan, listOf(activity))
        farms.savePlan(pastPlan, emptyList())
        stage3.saveHarvest(
            HarvestRecord("demo-harvest", pastPlan.id, today.minusDays(28), 1_120.0, "kg", 26.0, 14_500.0, "Fictional demo harvest", now),
            listOf(ActualExpense("demo-expense", "demo-harvest", "Fictional inputs", 14_500.0)),
        )
        stage3.saveWeather(listOf(WeatherSnapshot("demo-weather", "Jaipur — DEMO", now, now, 27.0, 52.0, 11.0, 0.0, "Fictional clear", "DEMO — fictional weather")))
        stage3.saveMarketPrices(listOf(MarketPriceRecord("demo-market", "Wheat", "Rajasthan", "Jaipur", "Demo Mandi", 2300.0, 2500.0, 2400.0, today, "DEMO — fictional mandi price", now)))
    }
}
