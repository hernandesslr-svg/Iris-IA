package com.iris.assistant

import android.content.Context
import android.os.SystemClock
import java.util.Locale

/** Android-native, UI-independent adaptations of reusable Jarvis utilities. */
object IrisTextSanitizer {
    fun cleanSpeech(text: String): String = text
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("[\\u0000-\\u001F]"), " ")
        .replace(Regex("\\s+"), " ").trim()
}

class IrisEchoGuard {
    private var lastSpoken = ""
    private var spokenAt = 0L
    private fun normalize(text: String): String = text.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim()
    fun record(text: String) {
        lastSpoken = normalize(text)
        spokenAt = SystemClock.elapsedRealtime()
    }
    fun isEcho(text: String): Boolean {
        if (SystemClock.elapsedRealtime() - spokenAt > 10000L) return false
        val heard = normalize(text)
        return heard.length >= 8 && lastSpoken.contains(heard)
    }
}

enum class IrisActivity { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

class IrisActivityState {
    @Volatile var current = IrisActivity.IDLE
        private set
    fun change(next: IrisActivity) { current = next }
}

object IrisCommandSuggestions {
    fun forContext(context: String): List<String> = when(context.lowercase(Locale.ROOT)) {
        "device" -> listOf("Abra as configurações", "Aumente o volume", "Que horas são?")
        "study" -> listOf("Explique esse assunto", "Faça um resumo", "Me dê um exemplo")
        else -> listOf("Iris, quais são suas capacidades?", "Me ajude a estudar", "Explique uma dúvida")
    }
}

object IrisAudioFeedback {
    fun confirm(context: Context) {
        val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 35)
        try { tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 65) }
        finally { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 180) }
    }
}
