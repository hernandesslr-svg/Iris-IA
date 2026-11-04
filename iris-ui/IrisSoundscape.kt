package com.iris.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Android replacement for Jarvis music/sfx lifecycle, without web audio,
 * external files, copyrighted score or an extra background player.
 */
class IrisSoundscape(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var focus: AudioFocusRequest? = null
    private var enabled = false
    private var lastCue = 0L

    fun enable(value: Boolean) { enabled = value }
    fun isEnabled() = enabled

    fun cue(kind: String, listening: Boolean = false, speaking: Boolean = false) {
        if (!enabled || listening || speaking) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCue < 400) return
        lastCue = now
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 25)
        val type = when (kind) {
            "ready" -> ToneGenerator.TONE_PROP_ACK
            "complete" -> ToneGenerator.TONE_PROP_BEEP
            "error" -> ToneGenerator.TONE_PROP_NACK
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        try { tone.startTone(type, 85) }
        finally { main.postDelayed({ tone.release() }, 220) }
    }

    fun duckForSpeech() {
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()
            focus = request
            audio.requestAudioFocus(request)
        }
    }
    fun restoreAfterSpeech() {
        if (Build.VERSION.SDK_INT >= 26) focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }
    fun close() { restoreAfterSpeech(); enabled = false }
}
