package com.iris.assistant

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import java.text.Normalizer
import java.util.Locale

/**
 * Foreground microphone service. Android SpeechRecognizer may stop listening on some devices;
 * a dedicated offline wake-word engine is needed for reliable always-on detection.
 */
class IrisVoiceService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var neural: IrisNeuralCore? = null
    private var running = false
    private var hearing = false
    private var activeUntil = 0L
    private val retry = Runnable { listen() }
    private val channel = "iris_voice"
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel,"Iris - escuta de voz",NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this,channel)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Iris aguardando sua voz")
            .setContentText("Diga Iris para ativar por 50 segundos")
            .setContentIntent(open)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1001,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1001,notification)
        tts = TextToSpeech(this,this)
        neural = IrisNeuralCore(this)
        running = true
        handler.post { listen() }
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int = START_STICKY
    private fun listen() {
        if (!running || hearing || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) return
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            hearing = true
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            })
        } catch (_: Exception) { scheduleRetry() }
    }
    private fun scheduleRetry() {
        hearing = false
        handler.removeCallbacks(retry)
        if (running) handler.postDelayed(retry,1200)
    }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        scheduleRetry()
        if (!text.isNullOrBlank()) handleSpeech(text)
    }
    private fun handleSpeech(text:String) {
        val normalized = Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
        val wake = Regex("\\biris\\b").find(normalized)
        val now = SystemClock.elapsedRealtime()
        val command = if (wake != null) {
            activeUntil = now + 50_000
            text.substring(wake.range.last+1).trim(' ','.',',','!','?')
        } else if (now < activeUntil) {
            activeUntil = now + 50_000
            text
        } else return
        if (command.isBlank()) { tts?.speak("Estou ouvindo.",TextToSpeech.QUEUE_FLUSH,null,"iris-ready"); return }
        Thread {
            val answer = try { neural?.generateOrNull(command) } catch (_: Exception) { null }
            val spoken = answer ?: "Não consegui processar sua pergunta agora."
            sendBroadcast(Intent("com.iris.assistant.VOICE_REPLY").setPackage(packageName).putExtra("answer",spoken))
            handler.post { tts?.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,"iris-answer") }
        }.start()
    }
    override fun onError(error:Int) = scheduleRetry()
    override fun onReadyForSpeech(params:Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB:Float) {}
    override fun onBufferReceived(buffer:ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults:Bundle?) {}
    override fun onEvent(eventType:Int,params:Bundle?) {}
    override fun onInit(status:Int) { if(status==TextToSpeech.SUCCESS) tts?.language=Locale("pt","BR") }
    override fun onBind(intent:Intent?):IBinder? = null
    override fun onDestroy() {
        running=false
        handler.removeCallbacks(retry)
        recognizer?.cancel()
        recognizer?.destroy()
        tts?.shutdown()
        neural?.close()
        super.onDestroy()
    }
}
