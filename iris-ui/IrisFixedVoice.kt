package com.iris.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Single fixed Iris voice policy. No voice picker.
 *
 * Kokoro cannot be selected until its offline inference engine, Portuguese
 * phonemizer and voice/model assets are installed and validated on device.
 * The Android pt-BR engine remains the working fallback in the meantime.
 */
object IrisFixedVoice {
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
