package com.agroai.app.domain

import kotlin.math.*

data class YieldEstimate(val productionTonnesLow: Double, val productionTonnesHigh: Double, val revenueLow: Double, val revenueHigh: Double, val grossMarginLow: Double, val grossMarginHigh: Double)

object FarmCalculators {
    fun toHectares(value: Double, unit: String, bighaSquareMeters: Double? = null): Double {
        require(value > 0) { "Area must be positive" }
        return when (unit) {
            "hectare" -> value
            "acre" -> value * 0.40468564224
            "square_metre" -> value / 10_000.0
            "square_foot" -> value * 0.09290304 / 10_000.0
            "bigha" -> value * requireNotNull(bighaSquareMeters) { "Enter the local square metres per bigha" } / 10_000.0
            else -> error("Unsupported area unit")
        }
    }

    fun seedQuantity(areaHectares: Double, seedRateKgPerHectare: Double): Double {
        require(areaHectares > 0 && seedRateKgPerHectare > 0)
        return areaHectares * seedRateKgPerHectare
    }

    fun irrigationVolumeLitres(areaHectares: Double, depthMillimetres: Double, efficiencyPercent: Double): Double {
        require(areaHectares > 0 && depthMillimetres > 0 && efficiencyPercent in 1.0..100.0)
        return areaHectares * 10_000.0 * (depthMillimetres / 1_000.0) / (efficiencyPercent / 100.0) * 1_000.0
    }

    fun cultivationCost(expenses: List<FarmExpense>): Double {
        require(expenses.all { it.amount >= 0 })
        return expenses.sumOf { it.amount }
    }

    fun yieldRevenue(areaHectares: Double, yieldLowTonnesPerHectare: Double, yieldHighTonnesPerHectare: Double, pricePerKg: Double, expenses: Double): YieldEstimate {
        require(areaHectares > 0 && yieldLowTonnesPerHectare >= 0 && yieldHighTonnesPerHectare >= yieldLowTonnesPerHectare && pricePerKg >= 0 && expenses >= 0)
        val low = areaHectares * yieldLowTonnesPerHectare
        val high = areaHectares * yieldHighTonnesPerHectare
        val revenueLow = low * 1_000 * pricePerKg
        val revenueHigh = high * 1_000 * pricePerKg
        return YieldEstimate(low, high, revenueLow, revenueHigh, revenueLow - expenses, revenueHigh - expenses)
    }
}

object BoundaryMath {
    data class Result(val valid: Boolean, val areaSquareMeters: Double = 0.0, val error: String? = null)

    fun validate(points: List<GeoPoint>): Result {
        if (points.distinct().size < 3) return Result(false, error = "points")
        if (points.any { it.latitude !in -90.0..90.0 || it.longitude !in -180.0..180.0 }) return Result(false, error = "range")
        if (selfIntersects(points)) return Result(false, error = "cross")
        val area = areaSquareMeters(points)
        return if (area < 1.0) Result(false, error = "small") else Result(true, area)
    }

    fun areaSquareMeters(points: List<GeoPoint>): Double {
        require(points.size >= 3)
        val meanLat = Math.toRadians(points.map { it.latitude }.average())
        val earth = 6_371_008.8
        val xy = points.map { Math.toRadians(it.longitude) * earth * cos(meanLat) to Math.toRadians(it.latitude) * earth }
        return abs(xy.indices.sumOf { index ->
            val next = (index + 1) % xy.size
            xy[index].first * xy[next].second - xy[next].first * xy[index].second
        }) / 2.0
    }

    private fun selfIntersects(points: List<GeoPoint>): Boolean {
        fun orientation(a: GeoPoint, b: GeoPoint, c: GeoPoint) = (b.longitude - a.longitude) * (c.latitude - a.latitude) - (b.latitude - a.latitude) * (c.longitude - a.longitude)
        fun crosses(a: GeoPoint, b: GeoPoint, c: GeoPoint, d: GeoPoint): Boolean {
            val o1 = orientation(a, b, c); val o2 = orientation(a, b, d); val o3 = orientation(c, d, a); val o4 = orientation(c, d, b)
            return o1 * o2 < 0 && o3 * o4 < 0
        }
        for (i in points.indices) for (j in i + 1 until points.size) {
            val i2 = (i + 1) % points.size; val j2 = (j + 1) % points.size
            if (i == j || i2 == j || j2 == i) continue
            if (crosses(points[i], points[i2], points[j], points[j2])) return true
        }
        return false
    }
}
