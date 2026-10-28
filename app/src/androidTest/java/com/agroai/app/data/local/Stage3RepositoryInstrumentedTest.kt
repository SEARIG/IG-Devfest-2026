package com.agroai.app.data.local

import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agroai.app.R
import com.agroai.app.data.*
import com.agroai.app.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class Stage3RepositoryInstrumentedTest {
    private lateinit var database: AgroDatabase
    private lateinit var farms: RoomFarmRepository
    private lateinit var stage3: RoomStage3Repository

    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AgroDatabase::class.java,
    )

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AgroDatabase::class.java).allowMainThreadQueries().build()
        farms = RoomFarmRepository(database.farmDao())
        stage3 = RoomStage3Repository(database.stage3Dao())
    }

    @After fun close() = database.close()

    @Test fun stage3RecordsAndFarmerContextRoundTrip() = runTest {
        val plot = FarmPlot("plot", "North", 1.0, "hectare", "loamy", "yes")
        farms.savePlot(plot)
        val plan = CropPlanGenerator.create(plot, requireNotNull(CropCatalog.byId("wheat")), LocalDate.of(2026, 11, 10))
        farms.savePlan(plan, CropPlanGenerator.activities(plan))
        val conversation = AiConversation("c", "Farm", 1, 1)
        stage3.saveConversation(conversation)
        stage3.saveMessage(ChatMessage("m", conversation.id, "farmer", "plots", 2))
        stage3.saveInspection(CropInspection("i", plot.id, null, "Wheat", setOf("yellowing"), setOf("leaves"), "", listOf("nutrient_or_water_stress"), listOf("soil_moisture"), listOf("consult_local_expert"), 3))
        val harvest = HarvestRecord("h", plan.id, LocalDate.of(2027, 3, 12), 10.0, "quintal", 2_500.0, 12_000.0, "", 4)
        stage3.saveHarvest(harvest, listOf(ActualExpense("e", harvest.id, "Transport", 500.0)))
        val alert = FarmAlert("a", "activity:x:today", "Inspect", "today", plan.id, plot.id, "normal", 5, false, "calendar")
        stage3.saveAlerts(listOf(alert, alert))

        assertEquals("plots", stage3.messages("c").first().single().text)
        assertEquals(setOf("yellowing"), stage3.inspections.first().single().symptoms)
        assertEquals(1, stage3.harvests.first().size)
        assertEquals(1, stage3.expenses.first().size)
        assertEquals(1, stage3.alerts.first().size)
        val context = RepositoryFarmerContextProvider(farms, stage3).current()
        assertEquals("North", context.plots.single().name)
        assertEquals("h", context.harvests.single().id)
    }

    @Test fun migrationOneToTwoPreservesStage2Rows() {
        val name = "migration-stage3.db"
        migrations.createDatabase(name, 1).apply {
            execSQL("INSERT INTO farm_plots (id,name,area,areaUnit,soilType,irrigationMethod,cropHistory,updatedAt) VALUES ('p','Legacy',1.0,'acre','loamy','yes','',1)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 2, true, AgroDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT name FROM farm_plots WHERE id='p'").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals("Legacy", cursor.getString(0)) }
            db.query("SELECT count(*) FROM ai_conversations").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        }
    }

    @Test fun migrationTwoToThreePreservesRowsAsUnownedLocalData() {
        val name = "migration-cloud-sync.db"
        migrations.createDatabase(name, 2).apply {
            execSQL("INSERT INTO farm_plots (id,name,area,areaUnit,soilType,irrigationMethod,cropHistory,updatedAt) VALUES ('legacy','Legacy',1.0,'acre','loamy','yes','',1)")
            execSQL("INSERT INTO ai_conversations (id,title,createdAt,updatedAt) VALUES ('chat','Legacy chat',1,2)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 3, true, AgroDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT name, ownerUid FROM farm_plots WHERE id='legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy", cursor.getString(0))
                assertEquals(LOCAL_OWNER, cursor.getString(1))
            }
            db.query("SELECT title, ownerUid FROM ai_conversations WHERE id='chat'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legacy chat", cursor.getString(0))
                assertEquals(LOCAL_OWNER, cursor.getString(1))
            }
            db.query("SELECT count(*) FROM sync_records").use { cursor -> assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0)) }
        }
    }

    @Test fun accountScopePreventsCrossAccountRoomExposure() = runTest {
        val scope = AccountScope()
        val repository = RoomFarmRepository(database.farmDao(), scope)
        repository.savePlot(FarmPlot("local", "Local", 1.0, "acre", "loamy", "yes"))
        scope.useAuthenticated("farmer-a")
        assertTrue(repository.plots.first().isEmpty())
        repository.savePlot(FarmPlot("a", "Farmer A", 1.0, "acre", "loamy", "yes"))
        scope.useAuthenticated("farmer-b")
        assertTrue(repository.plots.first().isEmpty())
        scope.useAuthenticated("farmer-a")
        assertEquals("a", repository.plots.first().single().id)
        scope.useLocal()
        assertEquals("local", repository.plots.first().single().id)
    }

    @Test fun demoResetIsIsolatedAndNeverQueuesCloudWrites() = runTest {
        val scope = AccountScope()
        val queue = LocalSyncQueue(database) {}
        val demoFarms = RoomFarmRepository(database.farmDao(), scope, queue)
        val demoStage3 = RoomStage3Repository(database.stage3Dao(), scope, queue)
        demoFarms.savePlot(FarmPlot("local-real", "Local real data", 1.0, "acre", "loamy", "seasonal"))

        val manager = DemoDataManager(database, scope, demoFarms, demoStage3)
        manager.reset()
        assertEquals(DEMO_OWNER, scope.current)
        assertEquals("demo-plot", demoFarms.plots.first().single().id)
        assertEquals("demo-harvest", demoStage3.harvests.first().single().id)
        assertEquals("demo-expense", demoStage3.expenses.first().single().id)
        assertTrue(database.syncDao().all(DEMO_OWNER).isEmpty())

        manager.reset()
        assertEquals(1, demoFarms.plots.first().size)
        scope.useLocal()
        assertEquals("local-real", demoFarms.plots.first().single().id)
    }

    @Test fun localMutationsQueueIdempotentlyAndDeletionRemainsPending() = runTest {
        val scope = AccountScope().apply { useAuthenticated("farmer-a") }
        var schedules = 0
        val queue = LocalSyncQueue(database) { schedules++ }
        val repository = RoomFarmRepository(database.farmDao(), scope, queue)
        val plot = FarmPlot("plot", "North", 1.0, "acre", "loamy", "yes")
        repository.savePlot(plot)
        repository.savePlot(plot.copy(name = "North updated"))
        val queued = database.syncDao().all("farmer-a").single()
        assertEquals(CloudCollections.PLOTS, queued.collection)
        assertEquals(1, queued.localRevision)
        assertEquals("pending", queued.state)
        database.syncDao().upsert(queued.copy(remoteRevision = 1, state = "synced"))
        repository.savePlot(plot.copy(name = "North after sync"))
        assertEquals(2, database.syncDao().all("farmer-a").single().localRevision)
        repository.deletePlot(plot.id)
        assertEquals("delete", database.syncDao().all("farmer-a").single().operation)
        assertTrue(repository.plots.first().isEmpty())
        assertTrue(schedules >= 3)
    }

    @Test fun historicalLocalDataMovesOnlyAfterExplicitLink() = runTest {
        val scope = AccountScope()
        val queue = LocalSyncQueue(database) {}
        val repository = RoomFarmRepository(database.farmDao(), scope, queue)
        repository.savePlot(FarmPlot("legacy", "Legacy", 1.0, "acre", "loamy", "yes"))
        assertTrue(queue.hasLocalData())
        queue.linkLocalData("farmer-a")
        assertFalse(queue.hasLocalData())
        scope.useAuthenticated("farmer-a")
        assertEquals("legacy", repository.plots.first().single().id)
        assertEquals("pending", database.syncDao().all("farmer-a").single().state)
    }

    @Test fun cloudRecordsApplyWithoutCreatingAnotherUpload() = runTest {
        val scope = AccountScope().apply { useAuthenticated("farmer-a") }
        val repository = RoomFarmRepository(database.farmDao(), scope, LocalSyncQueue(database) {})
        RemoteRecordApplier(database).apply("farmer-a", CloudCollections.PLOTS, "remote", mapOf(
            "name" to "Remote", "area" to 2.0, "areaUnit" to "acre", "soilType" to "loamy",
            "irrigationMethod" to "seasonal", "cropHistory" to "", "clientUpdatedAt" to 1L,
        ), false)
        RemoteRecordApplier(database).apply("farmer-a", CloudCollections.BOUNDARIES, "boundary", mapOf(
            "plotId" to "remote", "encodedPoints" to "0,0;0,1;1,0", "areaSquareMeters" to 1.0,
            "clientUpdatedAt" to 1L,
        ), false)
        RemoteRecordApplier(database).apply("farmer-a", CloudCollections.BOUNDARIES, "boundary", emptyMap(), true)
        assertEquals("remote", repository.plots.first().single().id)
        assertTrue(repository.boundaries.first().isEmpty())
        assertTrue(database.syncDao().all("farmer-a").isEmpty())
    }

    @Test fun cloudConflictCanBeResolvedWithoutOverwritingSilently() = runTest {
        val scope = AccountScope().apply { useAuthenticated("farmer-a") }
        val repository = RoomFarmRepository(database.farmDao(), scope)
        repository.savePlot(FarmPlot("plot", "Local edit", 1.0, "acre", "loamy", "yes"))
        val cloudPayload = mapOf(
            "name" to "Cloud edit", "area" to 2.0, "areaUnit" to "acre", "soilType" to "loamy",
            "irrigationMethod" to "seasonal", "cropHistory" to "", "clientUpdatedAt" to 2L,
        )
        database.syncDao().upsert(SyncRecordEntity(
            "farmer-a", CloudCollections.PLOTS, "plot", "upsert", CloudPayloadCodec.encode(cloudPayload),
            1, 1, "conflict", 2, conflictPayload = CloudPayloadCodec.encode(cloudPayload), conflictRevision = 2,
        ))

        val context = ApplicationProvider.getApplicationContext<Context>()
        CloudSyncManager(context, database).useCloudConflicts("farmer-a", RemoteRecordApplier(database))

        assertEquals("Cloud edit", repository.plots.first().single().name)
        val resolved = database.syncDao().all("farmer-a").single()
        assertEquals("synced", resolved.state)
        assertEquals(2, resolved.remoteRevision)
    }

    @Test fun stage3StringsExistInEnglishAndHindi() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        fun value(language: String): String {
            val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            return base.createConfigurationContext(configuration).getString(R.string.crop_doctor_disclosure)
        }
        assertTrue(value("en").contains("not automatically diagnosed"))
        assertTrue(value("hi").contains("स्वचालित"))
        assertNotEquals(value("en"), value("hi"))
    }
}
