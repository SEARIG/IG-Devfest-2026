package com.agroai.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Stage3EnginesTest {
    private val fixedToday = LocalDate.of(2026, 11, 10)
    private val plot = FarmPlot("plot", "North", 2.0, "acre", "loamy", "seasonal")
    private val plan = CropPlanGenerator.create(plot, requireNotNull(CropCatalog.byId("wheat")), fixedToday)

    @Test fun assistantRoutesSavedContextAndUsesExistingSeedCalculator() = runTest {
        val atToday = fixedToday.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val context = FarmerContext(listOf(plot), listOf(plan), listOf(CropPlanActivity("a", plan.id, plot.id, "manual", "Inspect", atToday)), emptyList())
        val assistant = LocalAiAssistantProvider(today = { fixedToday })
        assertTrue(assistant.answer("What activities are scheduled today?", context, "en").text.contains("1"))
        assertTrue(assistant.answer("Show my plots", context, "en").cardLines.single().contains("North"))
        val seed = assistant.answer("How much wheat seed for 2 acres?", context, "en")
        assertTrue(seed.text.contains("80.9"))
        assertTrue(seed.text.contains("101.2"))
        assertTrue(assistant.answer("Predict next year's pest outbreak", context, "en").remoteEligible)
    }

    @Test fun speechStateRecoveryIsDeterministic() {
        assertEquals(SpeechState.Listening, SpeechStateMachine.next(SpeechState.Idle, SpeechEvent.StartListening))
        assertEquals(SpeechState.Processing, SpeechStateMachine.next(SpeechState.Listening, SpeechEvent.RecognitionReady))
        assertEquals(SpeechState.Error, SpeechStateMachine.next(SpeechState.Listening, SpeechEvent.Fail))
        assertEquals(SpeechState.Idle, SpeechStateMachine.next(SpeechState.Error, SpeechEvent.Stop))
    }

    @Test fun symptomMatcherNeverClaimsImageDiagnosisOrNumericConfidence() = runTest {
        val result = LocalCropDiagnosisProvider().assess(CropDiagnosisRequest("Wheat", setOf("yellowing", "insects"), setOf("leaves"), ""))
        assertFalse(result.imageModelUsed)
        assertEquals("symptom_match_only", result.uncertainty)
        assertTrue(result.possibleCauses.contains("nutrient_or_water_stress"))
        assertTrue(result.nextSteps.contains("avoid_unverified_treatment"))
    }

    @Test fun weatherRulesRequireSuppliedRecords() {
        assertTrue(WeatherAdvisoryEngine.evaluate(emptyList()).isEmpty())
        val record = WeatherSnapshot("w", "Udaipur", 1, 2, 41.0, 30.0, 30.0, 8.0, "verified", "provider")
        assertEquals(setOf("review_irrigation", "review_spraying", "monitor_heat_stress"), WeatherAdvisoryEngine.evaluate(listOf(record)).mapTo(mutableSetOf(), WeatherAdvisory::code))
    }

    @Test fun marketAndHarvestMathKeepUnitsExplicit() {
        val low = MarketPriceRecord("1", "Wheat", "Rajasthan", "Udaipur", "A", 1.0, 2.0, 100.0, fixedToday, "source", 1)
        val high = low.copy(id = "2", market = "B", modalPricePerQuintal = 120.0)
        assertEquals("B", MarketPriceAnalysis.compare(listOf(low, high)).first().market)
        assertEquals(12_000.0, MarketPriceAnalysis.estimatedGrossRevenue(100.0, 120.0), 0.0)
        val harvest = HarvestRecord("h", plan.id, plan.harvestStart.plusDays(2), 1_000.0, "kg", 25.0, 10_000.0, "", 1)
        val comparison = HarvestAnalytics.compare(plan, harvest)
        assertEquals(25_000.0, comparison.actualRevenue!!, 0.0)
        assertEquals(15_000.0, comparison.actualGrossMargin!!, 0.0)
        assertEquals(2, comparison.harvestDateDifferenceDays)
        assertFalse(comparison.estimatedYieldAvailable)
    }

    @Test fun alertsUseStableSourceKeysAndDoNotDuplicate() {
        val now = fixedToday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val activity = CropPlanActivity("a", plan.id, plot.id, "manual", "Inspect", now)
        val generated = AlertEngine.generate(listOf(activity, activity), listOf(plan.copy(status = "active", harvestStart = fixedToday.plusDays(10))), emptyList(), now)
        assertEquals(generated.map(FarmAlert::sourceKey).distinct().size, generated.size)
        assertTrue(generated.any { it.sourceKey == "activity:a:today" })
        assertTrue(generated.any { it.sourceKey == "harvest:${plan.id}" })
    }
}
