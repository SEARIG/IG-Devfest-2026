package com.agroai.app.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.FileProvider
import android.util.Base64
import com.agroai.app.domain.CropImageInput
import java.io.ByteArrayOutputStream
import com.agroai.app.domain.SpeechInputProvider
import com.agroai.app.domain.SpeechOutputProvider
import com.agroai.app.domain.SpeechState
import java.io.File
import java.util.Locale
import java.util.UUID

class AndroidSpeechInputProvider(private val context: Context) : SpeechInputProvider {
    private var recognizer: SpeechRecognizer? = null

    override fun start(language: String, onText: (String) -> Unit, onState: (SpeechState) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onState(SpeechState.Error)
            onError("speech_unavailable")
            return
        }
        cancel()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onState(SpeechState.Listening)
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = onState(SpeechState.Processing)
                override fun onError(error: Int) { onState(SpeechState.Error); onError(if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "speech_timeout" else "speech_failed") }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) onError("speech_no_result") else onText(text)
                    onState(SpeechState.Idle)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "hi") "hi-IN" else "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
    }

    override fun cancel() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
    override fun close() = cancel()
}

class AndroidSpeechOutputProvider(context: Context) : SpeechOutputProvider {
    private var ready = false
    private val main = Handler(Looper.getMainLooper())
    private var onState: (SpeechState) -> Unit = {}
    private val engine = TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { onState(SpeechState.Speaking) } }
            override fun onDone(utteranceId: String?) { main.post { onState(SpeechState.Idle) } }
            @Deprecated("Platform callback") override fun onError(utteranceId: String?) { main.post { onState(SpeechState.Error) } }
        })
    }

    override fun speak(text: String, language: String, onState: (SpeechState) -> Unit): Boolean {
        if (!ready || text.isBlank()) return false
        this.onState = onState
        val result = engine.setLanguage(Locale.forLanguageTag(if (language == "hi") "hi-IN" else "en-IN"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agro-${System.currentTimeMillis()}") == TextToSpeech.SUCCESS
    }

    override fun stop() { engine.stop(); onState(SpeechState.Idle) }
    override fun close() { engine.stop(); engine.shutdown() }
}

class InspectionImageStore(private val context: Context) {
    private val directory = File(context.filesDir, "inspection_images").apply { mkdirs() }

    fun newCaptureUri(): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(directory, "${UUID.randomUUID()}.jpg").apply { createNewFile() })

    fun import(source: Uri): Uri? = runCatching {
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use(input::copyTo) } ?: return null
        FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }.getOrNull()

    fun prepareUpload(source: Uri): CropImageInput = context.contentResolver.openInputStream(source)?.use { input ->
        BitmapFactory.decodeStream(input)
    }?.let { original ->
        require(original.width >= 64 && original.height >= 64) { "Image is too small" }
        val scale = minOf(1f, 1280f / maxOf(original.width, original.height))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        val output = ByteArrayOutputStream()
        check(resized.compress(Bitmap.CompressFormat.JPEG, 82, output)) { "Image could not be encoded" }
        val bytes = output.toByteArray()
        require(bytes.size <= 5_000_000) { "Image is too large" }
        val width = resized.width
        val height = resized.height
        if (resized !== original) resized.recycle()
        original.recycle()
        CropImageInput(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg", width, height)
    } ?: error("Image could not be read")
}
