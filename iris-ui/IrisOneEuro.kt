package com.iris.assistant

import kotlin.math.PI
import kotlin.math.abs

/** Adaptive motion smoothing ported from jarvis-main/src/lib/oneEuro.ts. */
class IrisOneEuro(private val minCutoff: Double = 1.0, private val beta: Double = 0.0, private val derivativeCutoff: Double = 1.0) {
    private var lastTime: Double? = null
    private var value: Double? = null
    private var derivative: Double? = null
    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }
    fun filter(sample: Double, timeSeconds: Double): Double {
        val last = lastTime
        if (last == null) { lastTime = timeSeconds; value = sample; derivative = 0.0; return sample }
        val dt = (timeSeconds - last).coerceIn(1.0 / 240.0, 1.0 / 5.0)
        lastTime = timeSeconds
        val speed = (sample - (value ?: sample)) / dt
        val da = alpha(derivativeCutoff, dt)
        derivative = da * speed + (1.0 - da) * (derivative ?: 0.0)
        val a = alpha(minCutoff + beta * abs(derivative ?: 0.0), dt)
        value = a * sample + (1.0 - a) * (value ?: sample)
        return value!!
    }
    fun reset() { lastTime = null; value = null; derivative = null }
}
