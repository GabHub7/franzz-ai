package id.franzz.orbit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Robot Franzz melayang (bisa digeser) + panel chat. Semua jendela bertipe overlay aksesibilitas. */
class BotOverlay(private val svc: OrbitAccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val idle: Bitmap? = bmp("robot.png")
    private val blink: Bitmap? = bmp("robot_blink.png")

    private var bubble: ImageView? = null
    private var panel: LinearLayout? = null
    private var log: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var input: EditText? = null
    private var sendBtn: Button? = null
    private var useScreen: CheckBox? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var lastAnswer = ""
    private var speakNextReply = false

    private val chat = ArrayList<Pair<String, String>>()
    private var busy = false

    private val blinker = object : Runnable {
        override fun run() {
            val b = bubble ?: return
            if (blink != null) {
                b.setImageBitmap(blink)
                ui.postDelayed({ bubble?.setImageBitmap(idle) }, 160)
            }
            ui.postDelayed(this, 4500)
        }
    }

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()

    private fun bmp(name: String): Bitmap? = try {
        svc.assets.open(name).use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) { null }

    private fun params(w: Int, h: Int, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
    }

    fun show() { if (panel == null && bubble == null) showBubble() }

    fun hide() {
        removePanel()
        removeBubble()
    }

    // ---------------------------------------------------------------- robot melayang

    private fun showBubble() {
        val iv = ImageView(svc)
        iv.setImageBitmap(idle)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.contentDescription = "Bot Franzz"
        val p = params(dp(64), dp(64), false)
        p.gravity = Gravity.TOP or Gravity.START
        val pos = Store.pos(svc)
        p.x = pos.first
        p.y = pos.second
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        iv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (e.rawX - downX).toInt()
                    val my = (e.rawY - downY).toInt()
                    if (abs(mx) > dp(6) || abs(my) > dp(6)) moved = true
                    if (moved) {
                        p.x = startX + mx
                        p.y = startY + my
                        try { wm.updateViewLayout(iv, p) } catch (x: Exception) { }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) Store.setPos(svc, p.x, p.y) else openPanel()
                    true
                }
                else -> false
            }
        }
        try {
            wm.addView(iv, p)
            bubble = iv
            ui.removeCallbacks(blinker)
            ui.postDelayed(blinker, 4500)
        } catch (e: Exception) { bubble = null }
    }

    private fun removeBubble() {
        ui.removeCallbacks(blinker)
        bubble?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        bubble = null
    }

    // ---------------------------------------------------------------- panel chat

    private fun label(s: String, size: Float, bold: Boolean, color: Int): TextView {
        val t = TextView(svc)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun button(s: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(svc)
        b.text = s
        b.isAllCaps = false
        b.textSize = 13f
        b.setTextColor(if (primary) Color.WHITE else 0xFF1B1640.toInt())
        val bg = GradientDrawable()
        bg.cornerRadius = dp(10).toFloat()
        bg.setColor(if (primary) 0xFF6A4FE0.toInt() else 0xFFF1EFFC.toInt())
        b.background = bg
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(12), dp(8), dp(12), dp(8))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun openPanel() {
        if (panel != null) return
        removeBubble()
        val root = LinearLayout(svc)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(12))
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = dp(18).toFloat()
        bg.setStroke(dp(2), 0xFF1B1640.toInt())
        root.background = bg

        val head = LinearLayout(svc)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val icon = ImageView(svc)
        icon.setImageBitmap(idle)
        head.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)))
        val title = label("Bot Franzz", 16f, true, 0xFF1B1640.toInt())
        title.setPadding(dp(8), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = label("✕", 20f, true, 0xFF5A5780.toInt())
        close.setPadding(dp(12), dp(4), dp(4), dp(4))
        close.setOnClickListener { closePanel() }
        head.addView(close)
        root.addView(head)

        val sv = ScrollView(svc)
        val lg = LinearLayout(svc)
        lg.orientation = LinearLayout.VERTICAL
        sv.addView(lg)
        val svBg = GradientDrawable()
        svBg.setColor(0xFFF1EFFC.toInt())
        svBg.cornerRadius = dp(12).toFloat()
        sv.background = svBg
        sv.setPadding(dp(6), dp(6), dp(6), dp(6))
        val svLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220))
        svLp.topMargin = dp(8)
        root.addView(sv, svLp)

        val cb = CheckBox(svc)
        cb.text = "Sertakan layar yang sedang terbuka"
        cb.textSize = 13f
        cb.isChecked = true
        root.addView(cb)

        val row = LinearLayout(svc)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.BOTTOM
        val et = EditText(svc)
        et.hint = "Tulis pesan…"
        et.textSize = 14f
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        et.maxLines = 4
        row.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val mic = button("🎙", false) { startVoiceInput() }
        mic.contentDescription = "Tanya dengan suara"
        val micLp = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
        micLp.leftMargin = dp(4)
        row.addView(mic, micLp)
        val send = button("Kirim", true) { send() }
        val sendLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sendLp.leftMargin = dp(6)
        row.addView(send, sendLp)
        root.addView(row)

        val row2 = LinearLayout(svc)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(button("🔊", false) { if (lastAnswer.isNotBlank()) speak(lastAnswer) else addMsg("Belum ada jawaban untuk dibacakan.", false, false) }.apply { contentDescription = "Baca jawaban terakhir" })
        row2.addView(button("Isi kolom di layar", false) { fillFields() })
        val clr = button("Hapus chat", false) { chat.clear(); lg.removeAllViews() }
        val clrLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        clrLp.leftMargin = dp(8)
        row2.addView(clr, clrLp)
        val row2Lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row2Lp.topMargin = dp(8)
        root.addView(row2, row2Lp)

        val backKey = View.OnKeyListener { _, code, ev ->
            if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { closePanel(); true } else false
        }
        root.isFocusableInTouchMode = true
        root.setOnKeyListener(backKey)
        et.setOnKeyListener(backKey)

        val w = min(svc.resources.displayMetrics.widthPixels - dp(16), dp(400))
        val p = params(w, WindowManager.LayoutParams.WRAP_CONTENT, true)
        p.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        p.y = dp(8)
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        try {
            wm.addView(root, p)
        } catch (e: Exception) {
            showBubble()
            return
        }
        panel = root; log = lg; scroll = sv; input = et; sendBtn = send; useScreen = cb
        ensureTts()
        if (chat.isEmpty()) {
            addMsg("Halo! Aku bot Franzz. Tanyakan apa saja soal layar yang sedang terbuka, atau tekan \"Isi kolom di layar\" untuk mengisi formulir.", false, false)
        } else {
            chat.forEach { addMsg(it.second, it.first == "user", false) }
        }
    }

    private fun closePanel() {
        speakNextReply = false
        removePanel()
        showBubble()
    }

    private fun removePanel() {
        stopVoiceInput()
        speakNextReply = false
        try { tts?.stop() } catch (e: Exception) { }
        pendingSpeech = null
        panel?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        panel = null; log = null; scroll = null; input = null; sendBtn = null; useScreen = null
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(svc) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                if (engine != null) {
                    engine.language = Locale("id", "ID")
                    ttsReady = true
                    val pending = pendingSpeech
                    pendingSpeech = null
                    if (!pending.isNullOrBlank() && panel != null) speak(pending)
                }
            }
        }
    }

    private fun speak(text: String) {
        if (text.isBlank() || panel == null) return
        if (tts == null) ensureTts()
        if (!ttsReady) { pendingSpeech = text; return }
        try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "orbit-answer") }
        catch (e: Exception) { addMsg("Fitur suara tidak tersedia di perangkat ini.", false, true) }
    }

    private fun stopVoiceInput() {
        val old = recognizer
        recognizer = null
        try { old?.cancel(); old?.destroy() } catch (e: Exception) { }
    }

    private fun startVoiceInput() {
        if (busy) { addMsg("Tunggu jawaban sebelumnya selesai dulu.", false, false); return }
        if (svc.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            addMsg("Izin mikrofon diperlukan. Buka Franzz Orbit untuk mengizinkannya, lalu tekan 🎙 lagi.", false, false)
            try {
                svc.startActivity(Intent(svc, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("requestAudioPermission", true))
            } catch (e: Exception) { addMsg("Buka aplikasi Franzz Orbit dan aktifkan izin mikrofon.", false, true) }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(svc)) {
            addMsg("Pengenalan suara tidak tersedia di perangkat ini. Coba aktifkan layanan speech recognition, misalnya layanan suara Google.", false, true)
            return
        }
        stopVoiceInput()
        speakNextReply = true
        val prompt = addMsg("🎙 Mendengarkan…", false, false)
        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(svc)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { ui.post { prompt.text = "🎙 Silakan bicara…" } }
                override fun onBeginningOfSpeech() { }
                override fun onRmsChanged(rmsdB: Float) { }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() { ui.post { prompt.text = "Memproses ucapan…" } }
                override fun onError(error: Int) {
                    ui.post {
                        speakNextReply = false
                        prompt.text = "Ucapan belum tertangkap. Coba tekan 🎙 lagi dan bicara dekat mikrofon."
                        prompt.setTextColor(0xFFB3261E.toInt())
                        stopVoiceInput()
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    ui.post {
                        stopVoiceInput()
                        if (spoken.isBlank()) {
                            speakNextReply = false
                            prompt.text = "Ucapan tidak terdeteksi. Coba lagi."
                            prompt.setTextColor(0xFFB3261E.toInt())
                            return@post
                        }
                        prompt.text = "Kamu: $spoken"
                        val et = input
                        if (et != null) {
                            et.setText(spoken)
                            et.setSelection(et.text.length)
                            send()
                        } else {
                            speakNextReply = false
                            addMsg("Buka kembali panel chat lalu coba lagi.", false, true)
                        }
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) { }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) { }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            sr.startListening(intent)
        } catch (e: Exception) {
            speakNextReply = false
            stopVoiceInput()
            prompt.text = "Tidak bisa memulai mikrofon. Periksa izin atau layanan pengenalan suara."
            prompt.setTextColor(0xFFB3261E.toInt())
        }
    }

    private fun addMsg(text: String, mine: Boolean, err: Boolean): TextView {
        val t = TextView(svc)
        t.text = text
        t.textSize = 14f
        t.setPadding(dp(10), dp(7), dp(10), dp(7))
        t.setTextColor(if (mine) Color.WHITE else if (err) 0xFFB3261E.toInt() else 0xFF1B1640.toInt())
        val bg = GradientDrawable()
        bg.cornerRadius = dp(12).toFloat()
        bg.setColor(if (mine) 0xFF6A4FE0.toInt() else Color.WHITE)
        t.background = bg
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(3), 0, dp(3))
        lp.gravity = if (mine) Gravity.END else Gravity.START
        log?.addView(t, lp)
        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
        return t
    }

    private fun setError(v: TextView, text: String) {
        v.text = text
        v.setTextColor(0xFFB3261E.toInt())
    }

    // ---------------------------------------------------------------- chat

    private fun screenContext(): JSONObject? {
        val s = ScreenReader.read(svc) ?: return null
        val sb = StringBuilder(s.text)
        if (s.fields.isNotEmpty()) {
            sb.append("\n\nKolom isian di layar:\n")
            s.fields.forEachIndexed { i, f ->
                sb.append("${i + 1}. [${f.type}] ${f.title}")
                if (f.options.isNotEmpty()) sb.append(" | pilihan: ").append(f.options.joinToString(" / "))
                sb.append('\n')
            }
        }
        return JSONObject().put("url", s.pkg).put("title", "Layar aplikasi ${s.pkg}").put("text", sb.toString().take(30000))
    }

    private fun send() {
        val et = input ?: return
        val q = et.text.toString().trim()
        if (q.isEmpty() || busy) return
        busy = true
        sendBtn?.isEnabled = false
        et.setText("")
        chat.add(Pair("user", q))
        addMsg(q, true, false)
        val wait = addMsg("…", false, false)
        val withScreen = useScreen?.isChecked == true
        val msgs = JSONArray()
        chat.takeLast(12).forEach { msgs.put(JSONObject().put("role", it.first).put("text", it.second)) }
        Thread {
            try {
                val body = JSONObject().put("messages", msgs)
                if (withScreen) screenContext()?.let { body.put("page", it) }
                val out = Api.call(svc, "/api/chat", body).optString("text", "")
                ui.post {
                    wait.text = if (out.isEmpty()) "(kosong)" else out
                    chat.add(Pair("assistant", out))
                    lastAnswer = out
                    if (speakNextReply) speak(out)
                    speakNextReply = false
                }
            } catch (e: Exception) {
                ui.post {
                    speakNextReply = false
                    if (chat.isNotEmpty()) chat.removeAt(chat.size - 1)
                    setError(wait, Api.describe(e))
                }
            } finally {
                ui.post { busy = false; sendBtn?.isEnabled = true }
            }
        }.start()
    }

    // ---------------------------------------------------------------- isi kolom

    private fun fillFields() {
        if (busy) return
        busy = true
        val m = addMsg("Membaca layar…", false, false)
        Thread {
            try {
                val s = ScreenReader.read(svc)
                if (s == null || s.fields.isEmpty()) {
                    ui.post { m.text = "Tidak ada kolom isian di layar. Buka halaman yang berisi formulir lalu coba lagi." }
                    return@Thread
                }
                ui.post { m.text = "Mencari jawaban untuk ${s.fields.size} kolom…" }
                val qs = JSONArray()
                s.fields.forEach { qs.put(it.toJson()) }
                val r = Api.call(svc, "/api/gemini", JSONObject().put("questions", qs).put("profile", Store.profile(svc)))
                val arr = r.optJSONArray("answers") ?: JSONArray()
                val clean = JSONArray()
                val lines = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    if (a.optBoolean("skip", false)) continue
                    val f = s.fields.firstOrNull { it.id == a.optString("id") } ?: continue
                    if (f.type == "text") {
                        val t = a.optString("text", "").trim().take(500)
                        if (t.isNotEmpty()) {
                            clean.put(JSONObject().put("id", f.id).put("text", t))
                            lines.add("${f.title} → $t")
                        }
                    } else {
                        val ch = a.optJSONArray("choices")
                        val ok = ArrayList<Int>()
                        if (ch != null) for (k in 0 until ch.length()) {
                            val x = ch.optInt(k, -1)
                            if (x in f.options.indices && x !in ok) ok.add(x)
                        }
                        val use = if (f.type == "multi") ok else ok.take(1)
                        if (use.isNotEmpty()) {
                            clean.put(JSONObject().put("id", f.id).put("choices", JSONArray(use)))
                            lines.add("${f.title} → " + use.joinToString(", ") { f.options[it] })
                        }
                    }
                }
                ui.post {
                    if (clean.length() == 0) {
                        m.text = "Tidak ada jawaban yang cukup yakin. Isi manual."
                    } else {
                        m.text = "Jawaban untuk ${clean.length()} dari ${s.fields.size} kolom:\n" + lines.joinToString("\n")
                        val apply = button("Terapkan ke layar", true) {
                            val n = ScreenReader.apply(s.fields, clean)
                            addMsg("Terisi $n kolom. Periksa dulu, lalu kirim sendiri.", false, false)
                        }
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, dp(2), 0, dp(6))
                        log?.addView(apply, lp)
                        scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            } catch (e: Exception) {
                ui.post { setError(m, Api.describe(e)) }
            } finally {
                ui.post { busy = false }
            }
        }.start()
    }
}
