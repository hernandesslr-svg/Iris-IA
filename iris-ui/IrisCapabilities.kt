package com.iris.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech

/** Android-native capability probe inspired by Jarvis capabilities.ts. */
object IrisCapabilities {
    data class Status(
        val microphonePermission: Boolean,
        val speechRecognition: Boolean,
        val offlineRecognizerAvailable: Boolean,
        val weightsPresent: Boolean,
        val neuralLibraryPresent: Boolean
    )
    fun probe(context: Context): Status {
        val mic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val speech = SpeechRecognizer.isRecognitionAvailable(context)
        val offline = if (android.os.Build.VERSION.SDK_INT >= 31)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) else false
        val weights = try { context.assets.open("iris-lm-weights.json").use { true } } catch (_: Exception) { false }
        val neural = try { context.assets.list("")?.any { it.endsWith(".gguf") } == true } catch (_: Exception) { false }
        return Status(mic, speech, offline, weights, neural)
    }
}
