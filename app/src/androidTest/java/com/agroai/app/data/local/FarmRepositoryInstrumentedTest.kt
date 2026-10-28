package com.agroai.app.data.local

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agroai.app.R
import com.agroai.app.data.RoomFarmRepository
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FarmRepositoryInstrumentedTest {
    private lateinit var database: AgroDatabase
    private lateinit var repository: RoomFarmRepository

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgroDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomFarmRepository(database.farmDao())
    }

    @After fun close() = database.close()

    @Test fun plotBoundaryPlanAndActivityRoundTripAndCascade() = runTest {
        val plot = FarmPlot("plot-1", "North", 1.0, "hectare", "loamy", "yes")
        repository.savePlot(plot)
        repository.saveBoundary(FarmBoundary("boundary-1", plot.id, listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.0)), 6_000.0))
        val crop = requireNotNull(CropCatalog.byId("wheat"))
        val plan = CropPlanGenerator.create(plot, crop, LocalDate.of(2026, 11, 10))
        repository.savePlan(plan, CropPlanGenerator.activities(plan))
        assertEquals(1, repository.plots.first().size)
        assertEquals(1, repository.boundaries.first().size)
        assertEquals(1, repository.plans.first().size)
        assertEquals(6, repository.activities.first().size)
        repository.deletePlot(plot.id)
        assertTrue(repository.plots.first().isEmpty())
        assertTrue(repository.boundaries.first().isEmpty())
        assertTrue(repository.plans.first().isEmpty())
        assertTrue(repository.activities.first().isEmpty())
    }

    @Test fun stageTwoStringsExistInEnglishAndHindi() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        fun value(language: String): String {
            val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            return base.createConfigurationContext(configuration).getString(R.string.planner_intro)
        }
        assertTrue(value("en").contains("rule-based"))
        assertTrue(value("hi").contains("नियम"))
        assertNotEquals(value("en"), value("hi"))
    }
}
