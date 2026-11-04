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
    private var memory: IrisMemory? = null
    private var statistical: IrisStatisticalModel? = null
    private var running = false
    private var hearing = false
    private var talking = false
    private var activeUntil = 0L
    private var lastPartial = ""
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
        memory = IrisMemory(this)
        statistical = IrisStatisticalModel(this)
        running = true
        handler.post { listen() }
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int = START_STICKY
    private fun listen() {
        if (!running || hearing || talking || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) return
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            hearing = true
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            })
        } catch (_: Exception) { scheduleRetry() }
    }
    private fun scheduleRetry() {
        hearing = false
        handler.removeCallbacks(retry)
        if (running && !talking) handler.postDelayed(retry,350)
    }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        val candidate = text ?: lastPartial
        lastPartial = ""
        scheduleRetry()
        if (candidate.isNotBlank()) handleSpeech(candidate)
    }
    // Adaptado da logica original iris-local.ts: remove repeticoes da transcricao.
    private fun hearOnce(raw:String):String {
        val words=raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        for (size in 1..(words.size/2)) {
            if (words.size % size != 0) continue
            val chunk=words.take(size)
            if (words.chunked(size).all { part -> part.map { it.lowercase(Locale.ROOT) } == chunk.map { it.lowercase(Locale.ROOT) } })
                return chunk.joinToString(" ")
        }
        return words.filterIndexed { index,word -> index==0 || !word.equals(words[index-1],ignoreCase=true) }.joinToString(" ")
    }
    private fun handleSpeech(text:String) {
        val cleaned = hearOnce(text)
        val normalized = Normalizer.normalize(cleaned.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
        val wake = Regex("\\biris\\b").find(normalized)
        val now = SystemClock.elapsedRealtime()
        val command = if (wake != null) {
            activeUntil = now + 50_000
            cleaned.substring(wake.range.last+1).trim(' ','.',',','!','?')
        } else if (now < activeUntil) {
            activeUntil = now + 50_000
            cleaned
        } else return
        if (command.isBlank()) { speak("Estou ouvindo.","iris-ready"); return }
        val remembered = memory?.teach(command) ?: memory?.recall(command)
        if (remembered != null) {
            sendBroadcast(Intent("com.iris.assistant.VOICE_REPLY").setPackage(packageName).putExtra("answer",remembered))
            speak(remembered,"iris-memory")
            return
        }
        val deviceResult = IrisDeviceControl.tryExecute(this,command)
        if (deviceResult != null) {
            sendBroadcast(Intent("com.iris.assistant.VOICE_REPLY").setPackage(packageName).putExtra("answer",deviceResult))
            speak(deviceResult,"iris-device")
            return
        }
        Thread {
            val intent = IrisIntent.classify(command)
            val mood = statistical?.mood(command)?.takeIf { it.confident }?.label ?: IrisIntent.mood(command)
            val topic = statistical?.topic(command)?.takeIf { it.confident }?.label ?: "geral"
            val prompt = when(intent) {
                IrisIntent.Kind.SEARCH -> "Pergunta factual. Se não souber ou exigir dados atuais, diga que não pode confirmar. Pergunta: $command"
                IrisIntent.Kind.CHAT -> "Converse naturalmente em português brasileiro, de forma breve, respeitando o humor $mood. Assunto: $topic. Mensagem: $command"
            }
            val answer = try { neural?.generateOrNull(prompt) } catch (_: Exception) { null }
            val spoken = answer ?: "Não consegui processar sua pergunta agora."
            sendBroadcast(Intent("com.iris.assistant.VOICE_REPLY").setPackage(packageName).putExtra("answer",spoken))
            handler.post { speak(spoken,"iris-answer") }
        }.start()
    }
    private fun speak(text:String,id:String) {
        talking = true
        handler.removeCallbacks(retry)
        recognizer?.cancel()
        hearing = false
        tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,id)
    }
    override fun onError(error:Int) { if (!talking) scheduleRetry() }
    override fun onReadyForSpeech(params:Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB:Float) {}
    override fun onBufferReceived(buffer:ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults:Bundle?) {
        lastPartial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }
    override fun onEvent(eventType:Int,params:Bundle?) {}
    override fun onInit(status:Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language=Locale("pt","BR")
        tts?.setSpeechRate(0.94f)
        tts?.setPitch(1.04f)
        val pt = tts?.voices?.filter { it.locale.language == "pt" && it.locale.country == "BR" }
        pt?.firstOrNull { !it.isNetworkConnectionRequired }?.let { tts?.voice = it }
        tts?.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId:String?) {}
            override fun onDone(utteranceId:String?) { handler.post { talking=false; scheduleRetry() } }
            override fun onError(utteranceId:String?) { handler.post { talking=false; scheduleRetry() } }
        })
    }
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
