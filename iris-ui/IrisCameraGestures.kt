package com.iris.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Opt-in front camera gesture input. No preview, frames never leave the device.
 * Detects horizontal hand-waving motion in a central region, NOT hand landmarks.
 */
class IrisCameraGestures(private val activity: Activity, private val onGesture: (String) -> Unit) {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private val active = AtomicBoolean(false)
    private var lastCenter = -1.0
    private var lastDirection = 0
    private var reversals = 0
    private var lastMotionAt = 0L
    private var lastTriggerAt = 0L
    private var previous: IntArray? = null
    private var landmarks: IrisHandLandmarks? = null
    private var lastLandmarkAt = 0L
    private var previousGesture: String? = null
    private var stableGestures = 0

    fun start(): Boolean {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        if (!active.compareAndSet(false, true)) return true
        val worker = HandlerThread("iris-camera-gestures").also { it.start() }
        thread = worker
        handler = Handler(worker.looper)
        try {
            landmarks = IrisHandLandmarks(activity)
            val manager = activity.getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: throw IllegalStateException("Câmera frontal indisponível")
            reader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2).also {
                it.setOnImageAvailableListener({ source ->
                    val image = try { source.acquireLatestImage() } catch (_: Exception) { null }
                    if (image != null) try { process(image) } finally { image.close() }
                }, handler)
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!active.get()) { device.close(); return }
                    camera = device
                    val surface = reader?.surface ?: return
                    device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            if (!active.get()) { s.close(); return }
                            session = s
                            try {
                                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
                                s.setRepeatingRequest(request.build(), null, handler)
                            } catch (_: Exception) { stop() }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { stop() }
                    }, handler)
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); stop() }
                override fun onError(device: CameraDevice, error: Int) { device.close(); stop() }
            }, handler)
            return true
        } catch (_: Exception) { stop(); return false }
    }

    private fun process(image: Image) {
        if (!active.get()) return
        val nowFrame = SystemClock.elapsedRealtime()
        if (nowFrame - lastLandmarkAt >= 220L) {
            lastLandmarkAt = nowFrame
            try {
                val gray = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                val yPlane = image.planes[0]
                val bytes = yPlane.buffer
                val colors = IntArray(image.width * image.height)
                for (row in 0 until image.height) for (col in 0 until image.width) {
                    val v = bytes.get(row * yPlane.rowStride + col * yPlane.pixelStride).toInt() and 255
                    colors[row * image.width + col] = android.graphics.Color.rgb(v,v,v)
                }
                gray.setPixels(colors, 0, image.width, 0, 0, image.width, image.height)
                val gesture = landmarks?.classify(gray)
                gray.recycle()
                if (gesture != null && gesture == previousGesture) stableGestures++
                else { previousGesture = gesture; stableGestures = 1 }
                if (gesture != null && stableGestures == 3 && nowFrame - lastTriggerAt > 1800) {
                    lastTriggerAt = nowFrame
                    activity.runOnUiThread { if (active.get()) onGesture(gesture) }
                }
            } catch (_: Exception) { }
        }
        val plane = image.planes[0]
        val buffer = plane.buffer
        val stride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val cells = IntArray(12 * 8)
        for (gy in 0 until 8) for (gx in 0 until 12) {
            val x = (gx * w / 12 + w / 24).coerceAtMost(w - 1)
            val y = (gy * h / 8 + h / 16).coerceAtMost(h - 1)
            cells[gy * 12 + gx] = buffer.get(y * stride + x * pixelStride).toInt() and 255
        }
        val old = previous
        previous = cells
        if (old == null) return
        var weight = 0.0
        var weightedX = 0.0
        for (y in 1..6) for (x in 2..9) {
            val idx = y * 12 + x
            val delta = abs(cells[idx] - old[idx])
            if (delta > 24) { weight += delta; weightedX += x * delta }
        }
        val now = SystemClock.elapsedRealtime()
        if (weight < 240.0) {
            if (now - lastMotionAt > 850) { reversals = 0; lastDirection = 0; lastCenter = -1.0 }
            return
        }
        val center = weightedX / weight
        if (lastCenter >= 0) {
            val shift = center - lastCenter
            val direction = when { shift > .55 -> 1; shift < -.55 -> -1; else -> 0 }
            if (direction != 0) {
                if (lastDirection != 0 && direction != lastDirection && now - lastMotionAt < 1100) reversals++
                lastDirection = direction
                if (reversals >= 2 && now - lastTriggerAt > 2200) {
                    lastTriggerAt = now
                    reversals = 0
                    activity.runOnUiThread { if (active.get()) onGesture("WAVE") }
                }
            }
        }
        lastCenter = center
        lastMotionAt = now
    }

    fun stop() {
        active.set(false)
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        session = null
        camera = null
        reader = null
        previous = null
        try { landmarks?.close() } catch (_: Exception) {}
        landmarks = null
        thread?.quitSafely()
        thread = null
        handler = null
    }
}
