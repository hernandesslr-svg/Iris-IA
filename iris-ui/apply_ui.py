from pathlib import Path
p=Path("android-project/app/src/main/java/com/iris/assistant/MainActivity.kt")
s=p.read_text()
start=s.index("    override fun onCreate(savedInstanceState:")
end=s.index("    private fun normalize(",start)
new=r'''    private lateinit var subtitle: TextView
    private lateinit var hud: IrisHudView
    private var recognizer: android.speech.SpeechRecognizer? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var listeningNow = false
    private var activeUntil = 0L
    private var stopped = false
    private val restartSpeech = Runnable { startSpeechRecognition() }
    private fun startSpeechRecognition() {
        if (stopped || isFinishing || listeningNow) return
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try {
            if (recognizer == null) {
                recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
                recognizer?.setRecognitionListener(object: android.speech.RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { hud.listening = true }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { finishRecognition() }
                    override fun onResults(results: Bundle?) {
                        val heard = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        finishRecognition()
                        if (!heard.isNullOrBlank()) processSpeech(heard)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            listeningNow = true
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: Exception) { finishRecognition() }
    }
    private fun finishRecognition() {
        listeningNow = false
        hud.listening = false
        handler.removeCallbacks(restartSpeech)
        if (!stopped) handler.postDelayed(restartSpeech, 750)
    }
    private fun processSpeech(heard: String) {
        val normalized = java.text.Normalizer.normalize(heard.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val match = Regex("\\biris\\b").find(normalized)
        val now = android.os.SystemClock.elapsedRealtime()
        if (match != null) {
            activeUntil = now + 50_000L
            val command = heard.substring(match.range.last + 1).trim(' ', ',', '.', '!', '?')
            if (command.isBlank()) subtitle.text = "Estou ouvindo..."
            else { db.save("Você",command); speakAnswer(command) }
        } else if (now < activeUntil) {
            activeUntil = now + 50_000L
            db.save("Você",heard)
            speakAnswer(heard)
        }
    }
    private fun requestSpeechPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),77)
        else startSpeechRecognition()
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode == 77 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startSpeechRecognition()
    }
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(restartSpeech)
        recognizer?.cancel()
        listeningNow = false
    }
    override fun onResume() {
        super.onResume()
        if (::hud.isInitialized) requestSpeechPermission()
    }

    private fun speakAnswer(message: String) {
        Thread {
            val answer = try { neural.generateOrNull(message) ?: respond(message) }
                catch (_: Exception) { respond(message) }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    db.save("Iris", answer)
                    subtitle.text = answer
                    hud.speaking = speechReady
                    if (speechReady) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "iris")
                }
            }
        }.start()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        db = ChatStore()
        neural = IrisNeuralCore(this)
        topics = JSONArray(assets.open("topics.json").bufferedReader().use { it.readText() })
        languageEngine = try { IrisLanguageEngine(this) } catch (_: Exception) { null }
        tts = TextToSpeech(this, this)
        val root = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 12, 12, 24)
        }
        root.addView(column, android.widget.FrameLayout.LayoutParams(-1,-1))
        val top = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 32f
            setTextColor(Color.rgb(80,202,255))
            gravity = Gravity.CENTER
            contentDescription = "Configurações"
            setOnClickListener {
                val options = arrayOf("Ouvir minha voz", "Digitar mensagem")
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Iris")
                    .setItems(options) { _, which ->
                        if (which == 0) { activeUntil = android.os.SystemClock.elapsedRealtime() + 50_000L; subtitle.text = "Estou ouvindo..." } else {
                            val field = EditText(this@MainActivity)
                            android.app.AlertDialog.Builder(this@MainActivity)
                                .setTitle("Mensagem para Iris")
                                .setView(field)
                                .setPositiveButton("Enviar") { _, _ ->
                                    val msg=field.text.toString().trim()
                                    if(msg.isNotEmpty()) { db.save("Você",msg); speakAnswer(msg) }
                                }.setNegativeButton("Cancelar",null).show()
                        }
                    }.show()
            }
        }
        top.addView(settings,LinearLayout.LayoutParams(64,64))
        column.addView(top,LinearLayout.LayoutParams(-1,64))
        hud = IrisHudView(this).apply {
            contentDescription = "Núcleo falante Iris. Toque para falar"
            setOnClickListener { activeUntil = android.os.SystemClock.elapsedRealtime() + 50_000L; subtitle.text = "Estou ouvindo..." }
        }
        column.addView(hud,LinearLayout.LayoutParams(-1,0,1f))
        subtitle = TextView(this).apply {
            text = db.read().lastOrNull { it.first == "Iris" }?.second ?: "OLÁ! COMO POSSO TE AJUDAR HOJE?"
            setTextColor(Color.rgb(145,224,255))
            textSize = 19f
            typeface = Typeface.create("sans-serif-light",Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(12,16,12,16)
            maxLines = 5
        }
        column.addView(subtitle,LinearLayout.LayoutParams(-1,160))
        entry = EditText(this)
        setContentView(root)
        requestSpeechPermission()
        tts?.setOnUtteranceProgressListener(object: android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { hud.speaking = true } }
            override fun onDone(id: String?) { runOnUiThread { hud.speaking = false } }
            override fun onError(id: String?) { runOnUiThread { hud.speaking = false } }
        })
    }
'''
s=s[:start]+new+s[end:]
s=s.replace('override fun onDestroy(){ tts?.shutdown();', 'override fun onDestroy(){ stopped = true; handler.removeCallbacks(restartSpeech); recognizer?.destroy(); neural.close(); tts?.shutdown();')
p.write_text(s)
print("Interface Iris instalada",p.stat().st_size)
