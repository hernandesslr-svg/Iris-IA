package com.iris.assistant

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class IrisNeuralCore(private val context: Context) {
    companion object {
        const val MODEL_FILENAME = "qwen3-1.7b-q4.gguf"
        const val MIN_MODEL_BYTES = 900_000_000L
        init { System.loadLibrary("iris_native") }
    }
    private val lock = Any()
    private var initialized = false
    val modelFile: File get() = File(context.filesDir, "models/$MODEL_FILENAME")

    private external fun nativeLoad(path: String): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeClose()

    fun ensureModelInstalled(): File {
        val target = modelFile
        if (target.isFile && target.length() >= MIN_MODEL_BYTES) return target
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "$MODEL_FILENAME.partial")
        try {
            context.assets.open(MODEL_FILENAME).use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            check(tmp.length() >= MIN_MODEL_BYTES) { "Modelo GGUF incompleto" }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "Nao foi possivel instalar modelo" }
            return target
        } finally { tmp.delete() }
    }

    fun generateOrNull(prompt: String): String? = synchronized(lock) {
        require(prompt.length <= 100_000)
        if (!initialized) {
            initialized = nativeLoad(ensureModelInstalled().absolutePath)
            if (!initialized) return@synchronized null
        }
        nativeGenerate(prompt, 256).trim().ifBlank { null }
    }
    fun close() = synchronized(lock) { if (initialized) nativeClose(); initialized = false }
}
