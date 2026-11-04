package com.iris.assistant

import android.os.SystemClock

/** Local diagnostics with no network or private transcript storage. */
class IrisVoiceDiagnostics {
    private var lastErrorCode = 0
    private var lastErrorAt = 0L
    private var recognitionCount = 0
    private var lastRecognitionAt = 0L
    fun recordError(code: Int) { lastErrorCode = code; lastErrorAt = SystemClock.elapsedRealtime() }
    fun recordRecognition() { recognitionCount++; lastRecognitionAt = SystemClock.elapsedRealtime() }
    fun snapshot(): Snapshot = Snapshot(lastErrorCode, lastErrorAt, recognitionCount, lastRecognitionAt)
    data class Snapshot(val lastErrorCode: Int, val lastErrorAtMs: Long, val recognitionCount: Int, val lastRecognitionAtMs: Long)
}
