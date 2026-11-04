package com.iris.assistant

import kotlin.math.max

/**
 * Energy-based voice activity gate adapted from jarvis-main/src/lib/vad.ts.
 * Works on normalized RMS samples, without browser APIs or a Node server.
 * This detects acoustic activity, not words; transcription stays with Android.
 */
class IrisAudioGate {
    data class Meter(val energy: Double, val floor: Double, val threshold: Double, val speaking: Boolean)
    private var floor = 0.01
    private var startedAt = 0L
    private var quietAt = 0L
    private var speaking = false
    private var guarded = false
    fun setGuard(enabled: Boolean) { guarded = enabled }
    fun update(energy: Double, nowMs: Long): Boolean {
        val e = energy.coerceIn(0.0, 1.0)
        val threshold = max(0.012, floor * 2.6 * if (guarded) 2.4 else 1.0)
        if (!speaking) {
            if (e < threshold) floor += (e - floor) * if (e < floor) 0.02 else 0.0008
            if (e > threshold) {
                if (startedAt == 0L) startedAt = nowMs
                if (nowMs - startedAt >= 110) { speaking = true; quietAt = 0L }
            } else startedAt = 0L
        } else {
            if (e < threshold * 0.6) {
                if (quietAt == 0L) quietAt = nowMs
                if (nowMs - quietAt >= 650) { speaking = false; startedAt = 0L }
            } else quietAt = 0L
        }
        return speaking
    }
    fun meter(energy: Double) = Meter(energy, floor, max(0.012, floor * 2.6 * if (guarded) 2.4 else 1.0), speaking)
}
