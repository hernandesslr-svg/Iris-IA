package com.iris.assistant

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Eve via xAI; credentials encrypted on device. Falls back when unavailable. */
class IrisEveSpeech(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var serial = 0
    private var player: MediaPlayer? = null
    private val prefs = context.getSharedPreferences("iris_voice_auth", Context.MODE_PRIVATE)
    private val alias = "iris_xai_voice_aes"

    fun saveApiKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) { prefs.edit().remove("key").apply(); return }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        val secret = store.getKey(alias, null) as javax.crypto.SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val bytes = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("key", Base64.encodeToString(cipher.iv + bytes, Base64.NO_WRAP)).apply()
    }

    private fun readApiKey(): String? = try {
        val encoded = prefs.getString("key", null) ?: return null
        val data = Base64.decode(encoded, Base64.DEFAULT)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = store.getKey(alias, null) as javax.crypto.SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, data.copyOfRange(0, 12)))
        String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
    } catch (_: Exception) { null }

    fun configured(): Boolean = !readApiKey().isNullOrBlank()

    /** Returns true when an online attempt was started. Completion callback runs on main thread. */
    fun speak(text: String, onFallback: () -> Unit, onDone: () -> Unit): Boolean {
        val token = readApiKey() ?: return false
        val requestId = ++serial
        stopPlayer()
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.x.ai/v1/tts").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 20000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("text", text.take(12000))
                    .put("voice_id", IrisFixedVoice.onlineVoiceId).put("language", "pt-BR")
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode != 200) throw IllegalStateException("TTS HTTP ${connection.responseCode}")
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size < 256) throw IllegalStateException("Audio vazio")
                val file = java.io.File.createTempFile("iris-eve-", ".mp3", context.cacheDir)
                file.writeBytes(bytes)
                main.post {
                    if (requestId != serial) { file.delete(); return@post }
                    try {
                        val mp = MediaPlayer()
                        player = mp
                        mp.setDataSource(file.absolutePath)
                        mp.setOnCompletionListener { it.release(); if (player === it) player = null; file.delete(); onDone() }
                        mp.setOnErrorListener { p, _, _ -> p.release(); if (player === p) player = null; file.delete(); onFallback(); true }
                        mp.prepare()
                        mp.start()
                    } catch (_: Exception) { file.delete(); onFallback() }
                }
            } catch (_: Exception) { main.post { if (requestId == serial) onFallback() } }
            finally { connection?.disconnect() }
        }.start()
        return true
    }

    private fun stopPlayer() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
    }
    fun stop() { serial++; main.post { stopPlayer() } }
}
