package com.iris.assistant

import kotlin.math.max

/** Optional clap transient detector adapted from Jarvis clap.ts.
 * Feed calibrated PCM RMS windows (not SpeechRecognizer dB values).
 * It never activates the assistant unless the caller explicitly enables it.
 */
class IrisClapDetector {
    private val history = ArrayDeque<Double>()
    private var floor = 0.01
    private var candidateAt = -1L
    private var peak = 0.0
    private var lastClap = -1200L
    var detected = 0
        private set
    fun sample(level: Double, nowMs: Long): Boolean {
        val rms = level.coerceIn(0.0, 1.0)
        history.addLast(rms)
        if (history.size > 20) history.removeFirst()
        if (candidateAt >= 0) {
            peak = max(peak, rms)
            val elapsed = nowMs - candidateAt
            if (elapsed < 130 && rms < peak * 0.35) {
                candidateAt = -1L
                lastClap = nowMs
                detected++
                return true
            }
            if (elapsed > 260) candidateAt = -1L
            return false
        }
        if (rms < floor * 3) floor = max(0.002, floor + (rms - floor) * 0.05)
        if (nowMs - lastClap < 1200 || rms < 0.055 || rms < floor * 7) return false
        val before = history.elementAtOrNull(history.size - 1 - 5) ?: return false
        if (before > rms * 0.16) return false
        candidateAt = nowMs
        peak = rms
        return false
    }
}
