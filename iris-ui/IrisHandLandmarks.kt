package com.iris.assistant

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

/**
 * MediaPipe offline hand landmarks. Uses the bundled hand_landmarker.task asset.
 * This is distinct from the older image-motion heuristic.
 */
class IrisHandLandmarks(context: Context) : AutoCloseable {
    private val detector = HandLandmarker.createFromOptions(context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(1)
            .setMinHandDetectionConfidence(0.6f)
            .setMinHandPresenceConfidence(0.6f)
            .build())

    fun classify(bitmap: Bitmap): String? {
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        val hand = result.landmarks().firstOrNull() ?: return null
        if (hand.size < 21) return null
        val tips = intArrayOf(8, 12, 16, 20)
        val pips = intArrayOf(6, 10, 14, 18)
        val wrist = hand[0]
        fun distance(i: Int): Float {
            val p = hand[i]
            val dx = p.x() - wrist.x()
            val dy = p.y() - wrist.y()
            return dx * dx + dy * dy
        }
        val extended = tips.indices.map { distance(tips[it]) > distance(pips[it]) * 1.35f }
        return when {
            extended.all { it } -> "OPEN_PALM"
            extended.none { it } -> "FIST"
            extended[0] && extended[1] && !extended[2] && !extended[3] -> "VICTORY"
            else -> null
        }
    }
    override fun close() { detector.close() }
}
