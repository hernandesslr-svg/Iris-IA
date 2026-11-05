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
    private val ines by lazy { IrisInesSpeech(this) }
    private var neural: IrisNeuralCore? = null
    private var memory: IrisMemory? = null
    private var statistical: IrisStatisticalModel? = null
    private var trigram: IrisTrigramGenerator? = null
    private val audioGate = IrisAudioGate()
    private val audioLevel = IrisAudioLevel()
    private val clapDetector = IrisClapDetector()
    private var clapEnabled = false
    private var cuesEnabled = false
    private var speechRate = IrisFixedVoice.speechRate
    private var speechPitch = IrisFixedVoice.speechPitch
    private var cueVolume = 35
    private val audioManager by lazy { getSystemService(android.media.AudioManager::class.java) }
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private val fillers = IrisFillers()
    private val soundscape by lazy { IrisSoundscape(this) }
    private val diagnostics = IrisVoiceDiagnostics()
    private val echoGuard = IrisEchoGuard()
    private val activityState = IrisActivityState()
    private val micCoordinator = IrisMicrophoneCoordinator()
    @Volatile private var responseSerial = 0L
    private var microphoneActivity = false
    private var running = false
    private var hearing = false
    private var talking = false
    private var bargeInEnabled = true
    private var listeningDuringTts = false
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
        trigram = IrisTrigramGenerator(this)
        running = true
        handler.post { listen() }
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int {
        when (intent?.action) {
            "com.iris.assistant.STOP_SPEECH" -> {
                responseSerial++
                tts?.stop()
                talking = false
                audioGate.setGuard(false)
                activityState.change(IrisActivity.IDLE)
                scheduleRetry()
            }
            "com.iris.assistant.ENABLE_BARGE_IN" -> bargeInEnabled = true
            "com.iris.assistant.DISABLE_BARGE_IN" -> bargeInEnabled = false
            "com.iris.assistant.ENABLE_CLAPS" -> clapEnabled = true
            "com.iris.assistant.DISABLE_CLAPS" -> clapEnabled = false
            "com.iris.assistant.ENABLE_CUES" -> { cuesEnabled = true; soundscape.enable(true) }
            "com.iris.assistant.DISABLE_CUES" -> { cuesEnabled = false; soundscape.enable(false) }
            "com.iris.assistant.VOICE_SLOW" -> setSpeechRate(0.78f)
            "com.iris.assistant.VOICE_NORMAL" -> setSpeechRate(0.94f)
            "com.iris.assistant.VOICE_FAST" -> setSpeechRate(1.16f)
        }
        return START_STICKY
    }
    private fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.6f, 1.4f)
        tts?.setSpeechRate(speechRate)
    }
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            val request = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        }
    }
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }
    private fun listen() {
        if (!running || hearing || (talking && !bargeInEnabled) || checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) return
        if (!micCoordinator.acquire("speech")) return
        try {
            if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            hearing = true
            listeningDuringTts = talking
            if (!talking) activityState.change(IrisActivity.LISTENING)
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
        micCoordinator.release("speech")
        handler.removeCallbacks(retry)
        if (running && (!talking || bargeInEnabled)) handler.postDelayed(retry, if (talking) 650 else 350)
    }
    override fun onResults(results: Bundle?) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        val candidate = text ?: lastPartial
        lastPartial = ""
        scheduleRetry()
        if (talking) { if (isStopCommand(candidate)) stopSpeechAndListen(); return }
        if (candidate.isNotBlank() && !echoGuard.isEcho(candidate)) { diagnostics.recordRecognition(); handleSpeech(candidate) }
    }
    private fun isStopCommand(raw: String): Boolean {
        val normalized = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return listOf("iris pare", "iris parar", "pare de falar", "iris silencio", "cancele a resposta")
            .any { normalized.contains(it) }
    }
    private fun stopSpeechAndListen() {
        responseSerial++
        tts?.stop()
        talking = false
        releaseAudioFocus()
        listeningDuringTts = false
        audioGate.setGuard(false)
        activityState.change(IrisActivity.IDLE)
        scheduleRetry()
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
        val cleaned = hearOnce(IrisTextSanitizer.cleanSpeech(text))
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
        if (command.isBlank()) { speak(fillers.attention(),"iris-ready"); return }
        if (isStopCommand(command)) {
            responseSerial++
            tts?.stop()
            talking = false
            audioGate.setGuard(false)
            activityState.change(IrisActivity.IDLE)
            scheduleRetry()
            return
        }
        if (normalized.contains("sugira comandos") || normalized.contains("o que posso pedir")) {
            speak("Você pode dizer: " + IrisCommandSuggestions.forContext("general").joinToString(". "), "iris-suggestions")
            return
        }
        if (normalized.contains("fale mais devagar") || normalized.contains("voz mais devagar")) {
            setSpeechRate(0.78f); speak("Vou falar mais devagar.", "iris-rate"); return
        }
        if (normalized.contains("fale mais rapido") || normalized.contains("voz mais rapida")) {
            setSpeechRate(1.16f); speak("Vou falar mais rápido.", "iris-rate"); return
        }
        if (normalized.contains("velocidade normal") || normalized.contains("voz normal")) {
            setSpeechRate(0.94f); speak("Velocidade normal.", "iris-rate"); return
        }
        if (normalized.contains("ativar sons de confirmacao")) {
            cuesEnabled = true; soundscape.enable(true); speak("Sons de confirmação ativados.", "iris-cues"); return
        }
        if (normalized.contains("desativar sons de confirmacao")) {
            cuesEnabled = false; soundscape.enable(false); speak("Sons de confirmação desativados.", "iris-cues"); return
        }
        if (normalized.contains("diagnostico de voz")) {
            val d = diagnostics.snapshot()
            speak("Reconhecimentos nesta sessão: ${d.recognitionCount}. Último código de erro: ${d.lastErrorCode}.", "iris-diagnostics")
            return
        }
        if (normalized.contains("ativar palmas")) { clapEnabled = true; speak("Detecção de palmas ativada.", "iris-claps-on"); return }
        if (normalized.contains("desativar palmas")) { clapEnabled = false; speak("Detecção de palmas desativada.", "iris-claps-off"); return }
        if (normalized.contains("quais sao suas capacidades")) {
            val c = IrisCapabilities.probe(this)
            val reply = "Posso conversar, guardar informações e executar comandos compatíveis. " +
                (if (c.speechRecognition) "Reconhecimento de voz disponível. " else "Reconhecimento de voz indisponível. ") +
                (if (c.weightsPresent) "Pesos estatísticos instalados." else "Pesos estatísticos indisponíveis.")
            speak(reply, "iris-capabilities")
            return
        }
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
        val serial = ++responseSerial
        activityState.change(IrisActivity.THINKING)
        Thread {
            val intent = IrisIntent.classify(command)
            val mood = statistical?.mood(command)?.takeIf { it.confident }?.label ?: IrisIntent.mood(command)
            val topic = statistical?.topic(command)?.takeIf { it.confident }?.label ?: "geral"
            val prompt = when(intent) {
                IrisIntent.Kind.SEARCH -> "Pergunta factual. Se não souber ou exigir dados atuais, diga que não pode confirmar. Pergunta: $command"
                IrisIntent.Kind.CHAT -> "Converse naturalmente em português brasileiro, de forma breve, respeitando o humor $mood. Assunto: $topic. Mensagem: $command"
            }
            val answer = try { neural?.generateOrNull(prompt) } catch (_: Exception) { null }
            // Qwen continua principal; trigramas atuam como alternativa local para conversa.
            val local = if (answer.isNullOrBlank() && intent == IrisIntent.Kind.CHAT) {
                val moodKey = when (mood.lowercase(Locale.ROOT)) {
                    "neg", "negativo", "triste", "raiva" -> "neg"
                    "pos", "positivo", "feliz", "alegre" -> "pos"
                    else -> "neu"
                }
                val keys = listOf(
                    "Q:$topic:deep" to 3,
                    "Q:*:deep:$moodKey" to 2,
                    "Q:*:ask:$moodKey" to 1
                )
                try { trigram?.generate(keys, command.hashCode()) } catch (_: Exception) { null }
            } else null
            val spoken = answer?.takeIf { it.isNotBlank() }
                ?: local?.takeIf { it.length >= 8 }
                ?: "Não consegui processar sua pergunta agora."
            handler.post {
                if (running && serial == responseSerial) {
                    sendBroadcast(Intent("com.iris.assistant.VOICE_REPLY").setPackage(packageName).putExtra("answer",spoken))
                    speak(spoken,"iris-answer")
                }
            }
        }.start()
    }
    private fun speak(text:String,id:String) {
        talking = true
        requestAudioFocus()
        activityState.change(IrisActivity.SPEAKING)
        val safeText = IrisOutputSafety.spokenText(text)
        echoGuard.record(safeText)
        audioGate.setGuard(true)
        handler.removeCallbacks(retry)
        if (!bargeInEnabled) {
            recognizer?.cancel()
            hearing = false
            micCoordinator.release("speech")
        } else if (!hearing) handler.postDelayed({ if (running && talking) listen() }, 400)
        // Optional nonverbal feedback, suppressed during microphone capture.
        soundscape.cue("ready", listening = hearing, speaking = false)
        if (!ines.speak(safeText,
            onFallback = { tts?.speak(safeText,TextToSpeech.QUEUE_FLUSH,null,id) },
            onDone = { talking=false; releaseAudioFocus(); audioGate.setGuard(false); activityState.change(IrisActivity.IDLE); scheduleRetry() }
        )) tts?.speak(safeText,TextToSpeech.QUEUE_FLUSH,null,id)
    }
    override fun onError(error:Int) { diagnostics.recordError(error); if (!talking) scheduleRetry() }
    override fun onReadyForSpeech(params:Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB:Float) {
        // Android SpeechRecognizer reports relative dB, not calibrated PCM RMS.
        // This is an approximate activity diagnostic, not a replacement transcriber.
        val level = Math.pow(10.0, (rmsdB.toDouble() - 20.0) / 20.0).coerceIn(0.0, 1.0)
        microphoneActivity = audioGate.update(level, SystemClock.elapsedRealtime())
    }
    override fun onBufferReceived(buffer:ByteArray?) {
        if (buffer == null || buffer.size < 4 || talking) return
        // Android RecognitionListener delivers big-endian signed PCM16 when supported.
        // SpeechRecognizer audio buffers are little-endian PCM16 when supplied.
        val samples = ShortArray(buffer.size / 2) { i ->
            val lo = buffer[i * 2].toInt() and 0xff
            val hi = buffer[i * 2 + 1].toInt()
            ((hi shl 8) or lo).toShort()
        }
        val level = audioLevel.update(samples)
        microphoneActivity = audioGate.update(level, SystemClock.elapsedRealtime())
        if (clapEnabled && clapDetector.sample(level, SystemClock.elapsedRealtime())) {
            sendBroadcast(Intent("com.iris.assistant.CLAP_DETECTED").setPackage(packageName))
        }
    }
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults:Bundle?) {
        lastPartial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (talking && isStopCommand(lastPartial)) stopSpeechAndListen()
    }
    override fun onEvent(eventType:Int,params:Bundle?) {}
    override fun onInit(status:Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.let { IrisFixedVoice.configureAndroidFallback(it) }
        tts?.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId:String?) {}
            override fun onDone(utteranceId:String?) { handler.post { talking=false; releaseAudioFocus(); audioGate.setGuard(false); activityState.change(IrisActivity.IDLE); scheduleRetry() } }
            override fun onError(utteranceId:String?) { handler.post { talking=false; releaseAudioFocus(); audioGate.setGuard(false); activityState.change(IrisActivity.ERROR); scheduleRetry() } }
        })
    }
    override fun onBind(intent:Intent?):IBinder? = null
    override fun onDestroy() {
        running=false
        responseSerial++
        activityState.change(IrisActivity.IDLE)
        handler.removeCallbacks(retry)
        recognizer?.cancel()
        recognizer?.destroy()
        micCoordinator.release("speech")
        ines.stop()
        tts?.shutdown()
        soundscape.close()
        releaseAudioFocus()
        neural?.close()
        super.onDestroy()
    }
}
