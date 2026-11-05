package com.iris.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

/** Reprodutor local da voz Inês. Não usa serviços externos. */
class IrisInesSpeech(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    private var player: MediaPlayer? = null
    @Volatile private var lastError: String = ""
    fun errorDetail(): String = lastError

    fun speak(text: String, onFallback: () -> Unit, onDone: () -> Unit): Boolean {
        val token = ++generation
        Thread {
            var file: File? = null
            try {
                val model = File(context.filesDir, "loudkit")
                if (!File(model, ".ready").exists()) {
                    model.mkdirs()
                    fun extract(dir: String) {
                        for (entry in context.assets.list(dir).orEmpty()) {
                            val assetPath = if (dir.isEmpty()) entry else "$dir/$entry"
                            val children = context.assets.list(assetPath).orEmpty()
                            if (children.isNotEmpty()) extract(assetPath)
                            else {
                                val relative = assetPath.removePrefix("loudkit/")
                                val target = File(model, relative)
                                target.parentFile?.mkdirs()
                                context.assets.open(assetPath).use { input ->
                                    target.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        }
                    }
                    extract("loudkit")
                    File(model, ".ready").writeText("ok")
                }
                file = File.createTempFile("iris-ines-", ".wav", context.cacheDir)
                val result = IrisInesNative.synthesize(model.absolutePath, text, file.absolutePath)
                if (result != 0) throw IllegalStateException("Erro de síntese Inês: código $result")
                val audio = requireNotNull(file)
                main.post {
                    if (token != generation) { audio.delete(); return@post }
                    try {
                        val mp = MediaPlayer()
                        player = mp
                        mp.setDataSource(audio.absolutePath)
                        mp.setOnCompletionListener {
                            it.release()
                            if (player === it) player = null
                            audio.delete()
                            onDone()
                        }
                        mp.setOnErrorListener { p, _, _ ->
                            p.release()
                            if (player === p) player = null
                            audio.delete()
                            onFallback()
                            true
                        }
                        mp.prepare()
                        mp.start()
                    } catch (error: Exception) { lastError = error.toString(); android.util.Log.e("IrisInes", "Falha na reprodução", error); audio.delete(); onFallback() }
                }
            } catch (error: Throwable) {
                lastError = error.toString()
                android.util.Log.e("IrisInes", "Falha na síntese offline", error)
                file?.delete()
                main.post { if (token == generation) onFallback() }
            }
        }.start()
        return true
    }

    fun stop() {
        generation++
        main.post {
            try { player?.stop(); player?.release() } catch (_: Exception) {}
            player = null
        }
    }
}

object IrisInesNative {
    @Volatile private var initializedPath: String? = null
    private val loaded = try {
        System.loadLibrary("onnxruntime")
        System.loadLibrary("loudkit")
        true
    } catch (_: Throwable) { false }

    fun synthesize(model: String, text: String, wav: String): Int {
        if (!loaded) return -10
        return try {
            synchronized(this) {
                if (initializedPath != model) {
                    val init = nativeInit(model)
                    if (init != 0) return init
                    initializedPath = model
                }
                nativeSynthesize(text, wav)
            }
        } catch (error: Throwable) {
            android.util.Log.e("IrisInes", "Erro JNI Inês", error)
            -11
        }
    }

    private external fun nativeInit(model: String): Int
    private external fun nativeSynthesize(text: String, wav: String): Int
}
