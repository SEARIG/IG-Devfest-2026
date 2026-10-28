package com.agroai.app.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class AiConversation(val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val cardTitle: String? = null,
    val cardLines: List<String> = emptyList(),
)

data class AssistantReply(
    val text: String,
    val cardTitle: String? = null,
    val cardLines: List<String> = emptyList(),
    val remoteEligible: Boolean = false,
)

data class FarmerContext(
    val plots: List<FarmPlot>,
    val plans: List<CropPlan>,
    val activities: List<CalendarActivity>,
    val harvests: List<HarvestRecord>,
)

interface AiAssistantProvider {
    suspend fun answer(question: String, context: FarmerContext, language: String, history: List<ChatMessage> = emptyList()): AssistantReply
}

interface FarmerContextProvider {
    suspend fun current(): FarmerContext
}

interface AgronomyKnowledgeProvider {
    fun crop(idOrName: String): CropReference?
    fun recommendations(season: String, soil: String, irrigation: String): List<CropRecommendation>
}

interface ConversationRepository {
    val conversations: Flow<List<AiConversation>>
    fun messages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun saveConversation(conversation: AiConversation)
    suspend fun saveMessage(message: ChatMessage)
}

enum class SpeechState { Idle, Listening, Processing, Speaking, Error }
enum class SpeechEvent { StartListening, RecognitionReady, RecognitionComplete, StartSpeaking, Stop, Fail }

interface SpeechInputProvider : AutoCloseable {
    fun start(language: String, onText: (String) -> Unit, onState: (SpeechState) -> Unit, onError: (String) -> Unit)
    fun cancel()
}

interface SpeechOutputProvider : AutoCloseable {
    fun speak(text: String, language: String, onState: (SpeechState) -> Unit): Boolean
    fun stop()
}

data class CropInspection(
    val id: String,
    val plotId: String?,
    val imageUri: String?,
    val cropName: String,
    val symptoms: Set<String>,
    val affectedParts: Set<String>,
    val observations: String,
    val possibleCauses: List<String>,
    val evidenceNeeded: List<String>,
    val nextSteps: List<String>,
    val createdAt: Long,
    val imageModelUsed: Boolean = false,
)

data class DiagnosisResult(
    val possibleCauses: List<String>,
    val evidenceNeeded: List<String>,
    val nextSteps: List<String>,
    val uncertainty: String = "symptom_match_only",
    val imageModelUsed: Boolean = false,
)

data class CropImageInput(val base64: String, val mimeType: String, val width: Int, val height: Int)

data class CropDiagnosisRequest(
    val cropName: String,
    val symptoms: Set<String>,
    val affectedParts: Set<String>,
    val observations: String,
    val image: CropImageInput? = null,
    val language: String = "en",
)

interface CropDiagnosisProvider {
    suspend fun assess(request: CropDiagnosisRequest): DiagnosisResult
}

data class ExpertReviewRequest(
    val inspectionId: String,
    val farmerQuestion: String,
    val status: String = "draft",
)

data class WeatherSnapshot(
    val id: String,
    val location: String,
    val observedAt: Long,
    val forecastFor: Long,
    val temperatureC: Double,
    val humidityPercent: Double,
    val windKph: Double,
    val rainMillimetres: Double,
    val condition: String,
    val source: String,
)

data class WeatherAdvisory(val code: String, val sourceSnapshotId: String)

interface WeatherProvider {
    val snapshots: Flow<List<WeatherSnapshot>>
}

interface WeatherRefreshProvider {
    suspend fun fetch(location: String): List<WeatherSnapshot>
}

data class MarketPriceRecord(
    val id: String,
    val crop: String,
    val state: String,
    val district: String,
    val market: String,
    val minPricePerQuintal: Double,
    val maxPricePerQuintal: Double,
    val modalPricePerQuintal: Double,
    val dataDate: LocalDate,
    val source: String,
    val savedAt: Long,
)

interface MarketPriceProvider {
    val prices: Flow<List<MarketPriceRecord>>
}

interface MarketPriceRefreshProvider {
    suspend fun fetch(commodity: String, state: String, district: String): List<MarketPriceRecord>
}

data class HarvestRecord(
    val id: String,
    val planId: String,
    val actualHarvestDate: LocalDate,
    val quantity: Double,
    val unit: String,
    val sellingPricePerUnit: Double?,
    val actualTotalExpenses: Double,
    val notes: String,
    val createdAt: Long,
)

data class ActualExpense(val id: String, val harvestId: String, val label: String, val amount: Double)

data class HarvestComparison(
    val actualRevenue: Double?,
    val actualGrossMargin: Double?,
    val harvestDateDifferenceDays: Long,
    val estimatedYieldAvailable: Boolean,
    val estimatedCostAvailable: Boolean,
    val expectedRevenueAvailable: Boolean,
)

data class FarmAlert(
    val id: String,
    val sourceKey: String,
    val title: String,
    val message: String,
    val relatedPlanId: String?,
    val relatedPlotId: String?,
    val priority: String,
    val scheduledAt: Long,
    val read: Boolean,
    val action: String,
)
