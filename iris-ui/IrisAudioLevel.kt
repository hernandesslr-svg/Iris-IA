package com.iris.assistant

import kotlin.math.sqrt

/**
 * PCM audio level analyzer adapted from Jarvis audio.ts.
 * Uses one existing audio source; never opens a second microphone.
 * UI-agnostic: preserves Iris's original holographic circle.
 */
class IrisAudioLevel {
    private var smoothed = 0.0
    fun update(samples: ShortArray, count: Int = samples.size): Double {
        val size = count.coerceIn(0, samples.size)
        if (size == 0) return smoothed
        var sum = 0.0
        for (i in 0 until size) {
            val value = samples[i].toDouble() / 32768.0
            sum += value * value
        }
        val rms = sqrt(sum / size)
        smoothed = smoothed * 0.75 + rms * 0.25
        return smoothed.coerceIn(0.0, 1.0)
    }
    fun reset() { smoothed = 0.0 }
}
