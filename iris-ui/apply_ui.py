from pathlib import Path
p=Path("android-project/app/src/main/java/com/iris/assistant/MainActivity.kt")
s=p.read_text()
start=s.index("    override fun onCreate(savedInstanceState:")
end=s.index("    private fun normalize(",start)
new=r'''    private lateinit var subtitle: TextView
    private lateinit var hud: IrisHudView
    private fun startVoiceService() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),77)
            return
        }
        try {
            androidxNotNeededStartService()
        } catch (_: Exception) {
            subtitle.text = "Ative a escuta da Iris com o aplicativo visível."
        }
    }
    private fun androidxNotNeededStartService() {
        val intent = Intent(this,IrisVoiceService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent)
        else startService(intent)
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode == 77 && grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startVoiceService()
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
                        if (which == 0) { startVoiceService(); subtitle.text = "Diga Iris para começar." } else {
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
            setOnClickListener { startVoiceService(); subtitle.text = "Diga Iris para começar." }
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
        startVoiceService()
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
