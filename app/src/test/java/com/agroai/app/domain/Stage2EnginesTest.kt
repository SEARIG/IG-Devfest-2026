package com.agroai.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Stage2EnginesTest {
    @Test fun areaSeedWaterCostAndMarginUseDocumentedUnits() {
        val hectares = FarmCalculators.toHectares(2.0, "acre")
        assertEquals(0.80937128448, hectares, 0.00000001)
        assertEquals(80.937128448, FarmCalculators.seedQuantity(hectares, 100.0), 0.0001)
        assertEquals(250_000.0, FarmCalculators.irrigationVolumeLitres(1.0, 20.0, 80.0), 0.01)
        val expenses = listOf(FarmExpense("seed", 2_000.0), FarmExpense("labour", 3_000.0))
        assertEquals(5_000.0, FarmCalculators.cultivationCost(expenses), 0.0)
        val estimate = FarmCalculators.yieldRevenue(1.0, 2.0, 3.0, 20.0, 5_000.0)
        assertEquals(35_000.0, estimate.grossMarginLow, 0.0)
        assertEquals(55_000.0, estimate.grossMarginHigh, 0.0)
    }

    @Test fun bighaRequiresRegionalConvention() {
        assertThrows(IllegalArgumentException::class.java) { FarmCalculators.toHectares(1.0, "bigha") }
        assertEquals(0.25, FarmCalculators.toHectares(1.0, "bigha", 2_500.0), 0.0)
    }

    @Test fun boundaryRejectsTooFewAndSelfIntersectingPoints() {
        assertFalse(BoundaryMath.validate(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001))).valid)
        val bowTie = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.001), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.0))
        assertFalse(BoundaryMath.validate(bowTie).valid)
        val square = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.001), GeoPoint(0.001, 0.0))
        assertTrue(BoundaryMath.validate(square).valid)
        assertTrue(BoundaryMath.validate(square).areaSquareMeters > 12_000)
    }

    @Test fun recommendationAndPlanAreDeterministicAndExplainable() {
        val results = CropRecommendationEngine.recommend("rabi", "loamy", "no")
        assertEquals("chickpea", results.first().crop.id)
        assertTrue(results.first().reasons.contains("season_match"))
        val plot = FarmPlot("plot", "North", 1.0, "hectare", "loamy", "seasonal")
        val crop = requireNotNull(CropCatalog.byId("wheat"))
        val plan = CropPlanGenerator.create(plot, crop, LocalDate.of(2026, 11, 10))
        assertEquals(100.0, plan.seedQuantityKgLow, 0.0)
        assertEquals(LocalDate.of(2027, 3, 10), plan.harvestStart)
        assertEquals(6, CropPlanGenerator.activities(plan).size)
    }
}
