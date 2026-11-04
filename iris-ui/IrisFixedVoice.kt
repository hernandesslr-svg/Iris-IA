package com.iris.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Single fixed Iris voice policy. No voice picker.
 *
 * Online target: ElevenLabs Fernanda (fixed voice ID).
 * Offline fallback: Android pt-BR until a local neural voice is integrated.
 * Never bundle API secrets in the APK.
 */
object IrisFixedVoice {
    const val onlineProvider = "elevenlabs"
    const val onlineVoiceId = "KHmfNHtEjHhLK9eER20w"
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
        voices.sortedBy { it.name }.firstOrNull()?.let { tts.voice = it }
    }

    fun kokoroAssetsPresent(context: Context): Boolean {
        val assets = context.assets.list("kokoro")?.toSet().orEmpty()
        return "model.onnx" in assets && "voices.bin" in assets
    }
}
