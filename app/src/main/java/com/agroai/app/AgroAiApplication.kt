package com.agroai.app

import android.app.Application
import com.agroai.app.data.*
import com.agroai.app.data.local.AgroDatabase
import com.agroai.app.reminders.ReminderScheduler
import com.agroai.app.platform.InspectionImageStore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AgroAiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(val application: Application) {
    val preferences = PreferencesRepository(application)
    private val database = AgroDatabase.get(application)
    val accountScope = AccountScope()
    val cloudSyncManager = CloudSyncManager(application, database)
    val localSyncQueue = LocalSyncQueue(database, cloudSyncManager::schedule)
    val farmRepository = RoomFarmRepository(database.farmDao(), accountScope, localSyncQueue)
    val stage3Repository = RoomStage3Repository(database.stage3Dao(), accountScope, localSyncQueue)
    val farmerContextProvider = RepositoryFarmerContextProvider(farmRepository, stage3Repository)
    val inspectionImageStore = InspectionImageStore(application)
    val reminderScheduler = ReminderScheduler(application)
    val firebaseConfigured = runCatching {
        val app = FirebaseApp.initializeApp(application) ?: FirebaseApp.getInstance()
        app.options.projectId == "agro-ai-34b4a"
    }.getOrDefault(false)
    val auth = if (firebaseConfigured) AuthRepository(FirebaseAuth.getInstance()) else null
    val backendApi = BackendApi(BuildConfig.AGRO_AI_BACKEND_URL) { if (accountScope.current == DEMO_OWNER) null else auth?.idToken() }
    val assistantProvider = HybridAiAssistantProvider(com.agroai.app.domain.LocalAiAssistantProvider(), backendApi)
    val diagnosisProvider = HybridCropDiagnosisProvider(com.agroai.app.domain.LocalCropDiagnosisProvider(), backendApi)
    val weatherRefreshProvider = OpenMeteoWeatherProvider()
    val marketRefreshProvider = BackendMarketPriceProvider(backendApi)
    val demoData = DemoDataManager(database, accountScope, farmRepository, stage3Repository)
    private val firestore = if (firebaseConfigured) FirebaseFirestore.getInstance() else null
    val farmers = firestore?.let { FarmerRepository(it, database.syncDao()) }
    val remoteRecordApplier = RemoteRecordApplier(database)
    val cloudSyncEngine = firestore?.let { CloudSyncEngine(database, it, remoteRecordApplier) }
}
