package com.iris.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Single fixed Iris voice policy. No voice picker.
 *
 * Online target: xAI Eve (fixed voice ID).
 * Offline fallback: fixed local Android pt-BR voice; no voice selector.
 * A resemblance to Eve is not guaranteed across devices.
 * Never bundle API secrets in the APK.
 */
object IrisFixedVoice {
    const val onlineProvider = "xai"
    const val onlineVoiceId = "eve"
    val language: Locale = Locale("pt", "BR")
    const val speechRate = 0.94f
    const val speechPitch = 1.04f

    fun configureAndroidFallback(tts: TextToSpeech) {
        tts.language = language
        tts.setSpeechRate(speechRate)
        tts.setPitch(speechPitch)
        val voices = tts.voices.orEmpty()
            .filter { it.locale.language == "pt" && it.locale.country == "BR" && !it.isNetworkConnectionRequired }
        // Keep the selection stable on a given device rather than switching voices.
        voices.sortedWith(compareBy<android.speech.tts.Voice> { it.quality != android.speech.tts.Voice.QUALITY_VERY_HIGH }.thenBy { it.quality != android.speech.tts.Voice.QUALITY_HIGH }.thenBy { it.name }).firstOrNull()?.let { tts.voice = it }
    }

    fun kokoroAssetsPresent(context: Context): Boolean {
        val assets = context.assets.list("kokoro")?.toSet().orEmpty()
        return "model.onnx" in assets && "voices.bin" in assets
    }
}
