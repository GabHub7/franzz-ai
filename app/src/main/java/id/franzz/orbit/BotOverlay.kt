package id.franzz.orbit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Robot overlay. Semua menu mengikuti kontrak API web dan tidak membutuhkan API key di APK. */
class BotOverlay(private val svc: OrbitAccessibilityService) {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val idle: Bitmap? = bitmap("robot.png")
    private val blink: Bitmap? = bitmap("robot_blink.png")

    private var bubble: ImageView? = null
    private var panelRoot: LinearLayout? = null
    private var body: LinearLayout? = null
    private var bodyScroll: ScrollView? = null
    private var chatLogView: LinearLayout? = null
    private var chatInput: EditText? = null
    private var sendButton: Button? = null
    private var includeScreenCheck: CheckBox? = null
    private var includeScreen = false
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var speakNextReply = false
    private var lastAnswer = ""
    private var busy = false
    private var currentPage = "menu"
    private val backStack = ArrayList<String>()
    private val chat = ArrayList<ChatLine>()
    private var chatMode = ""
    private var resultKind = "text"
    private var resultTitle = "Hasil"
    private var resultText = ""
    private var resultData: JSONObject? = null
    private var fillFields: List<Field> = emptyList()
    private var fillAnswers = JSONArray()
    private var quizQuestions: List<QuizQuestion> = emptyList()
    private val quizAnswers = ArrayList<Any?>()
    private var quizCount = 5
    private var quizKind = "campur"

    private data class ChatLine(val role: String, val text: String)
    private data class QuizQuestion(val type: String, val question: String, val options: List<String>, val ref: Int, val why: String)

    private val ink = 0xFF201B49.toInt()
    private val muted = 0xFF706A8E.toInt()
    private val accent = 0xFF6B50E5.toInt()
    private val accent2 = 0xFF9B70F5.toInt()
    private val bg = 0xFFF6F4FF.toInt()
    private val line = 0xFFE3DDF5.toInt()
    private val error = 0xFFB3261E.toInt()
    private val success = 0xFF18794E.toInt()

    private fun dp(v: Int): Int = (v * svc.resources.displayMetrics.density).toInt()
    private fun bitmap(name: String): Bitmap? = try { svc.assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }

    private fun windowParams(w: Int, h: Int, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
    }

    fun show() { if (panelRoot == null && bubble == null) showBubble() }
    fun hide() { closePanel(showAgain = false); removeBubble() }
    fun dispose() {
        hide()
        stopVoice()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) { }
        tts = null
    }

    private fun showBubble() {
        if (bubble != null || Store.token(svc) == null || !Store.botOn(svc)) return
        val image = ImageView(svc).apply {
            setImageBitmap(idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Buka menu FRANZZ AI"
            background = ripple(0xFFFFFFFF.toInt(), accent, 32)
            elevation = dp(10).toFloat()
        }
        val p = windowParams(dp(66), dp(66), false)
        p.gravity = Gravity.TOP or Gravity.START
        val pos = Store.pos(svc)
        val metrics = svc.resources.displayMetrics
        p.x = pos.first.coerceIn(0, (metrics.widthPixels - dp(66)).coerceAtLeast(0))
        p.y = pos.second.coerceIn(dp(20), (metrics.heightPixels - dp(100)).coerceAtLeast(dp(20)))
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        image.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val mx = (event.rawX - downX).toInt()
                    val my = (event.rawY - downY).toInt()
                    if (abs(mx) > dp(5) || abs(my) > dp(5)) moved = true
                    if (moved) {
                        p.x = (startX + mx).coerceIn(0, (metrics.widthPixels - dp(66)).coerceAtLeast(0))
                        p.y = (startY + my).coerceIn(dp(20), (metrics.heightPixels - dp(100)).coerceAtLeast(dp(20)))
                        try { wm.updateViewLayout(image, p) } catch (_: Exception) { }
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
            wm.addView(image, p)
            bubble = image
            image.alpha = 0f
            image.scaleX = 0.82f; image.scaleY = 0.82f
            image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
            ui.removeCallbacks(blinker)
            ui.postDelayed(blinker, 4200)
        } catch (_: Exception) { bubble = null }
    }

    private val blinker = object : Runnable {
        override fun run() {
            val image = bubble ?: return
            image.animate().scaleX(1.05f).scaleY(1.05f).setDuration(180).withEndAction {
                image.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
            }.start()
            if (blink != null) {
                image.setImageBitmap(blink)
                ui.postDelayed({ if (bubble === image) image.setImageBitmap(idle) }, 180)
            }
            ui.postDelayed(this, 4600)
        }
    }

    private fun removeBubble() {
        ui.removeCallbacks(blinker)
        bubble?.let { image ->
            try { image.animate().cancel(); wm.removeView(image) } catch (_: Exception) { }
        }
        bubble = null
    }

    private fun openPanel() {
        if (panelRoot != null) return
        removeBubble()
        currentPage = "menu"
        backStack.clear()
        includeScreen = false
        val root = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.WHITE, bg)).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), line)
            }
            elevation = dp(14).toFloat()
            alpha = 0f
            translationY = dp(18).toFloat()
        }
        panelRoot = root
        renderPage()
        val metrics = svc.resources.displayMetrics
        val width = min(metrics.widthPixels - dp(20), dp(440)).coerceAtLeast(dp(280))
        val height = min(metrics.heightPixels - dp(48), dp(760)).coerceAtLeast(dp(360))
        val params = windowParams(width, height, true)
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(8)
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        params.windowAnimations = android.R.style.Animation_Dialog
        try {
            wm.addView(root, params)
            root.animate().alpha(1f).translationY(0f).setDuration(230).setInterpolator(DecelerateInterpolator()).start()
        } catch (_: Exception) {
            panelRoot = null
            showBubble()
        }
    }

    private fun closePanel(showAgain: Boolean = true) {
        stopVoice()
        speakNextReply = false
        try { tts?.stop() } catch (_: Exception) { }
        val root = panelRoot
        panelRoot = null
        body = null; bodyScroll = null; chatLogView = null; chatInput = null; sendButton = null; includeScreenCheck = null
        if (root != null) {
            try {
                root.animate().cancel()
                wm.removeView(root)
            } catch (_: Exception) { }
        }
        if (showAgain) showBubble()
    }

    private fun goTo(page: String) {
        backStack.add(currentPage)
        currentPage = page
        renderPage()
    }

    private fun goBack() {
        if (backStack.isNotEmpty()) {
            currentPage = backStack.removeAt(backStack.lastIndex)
            renderPage()
        } else closePanel()
    }

    private fun openChat(mode: String) {
        if (chatMode != mode) chat.clear()
        chatMode = mode
        includeScreen = false
        goTo("chat")
    }

    private fun titleForPage(): String = when (currentPage) {
        "menu" -> "FRANZZ AI"
        "study_menu" -> "Fitur AI"
        "chat" -> if (chatMode == "tutor") "Tutor AI" else "Chat AI"
        "fill" -> "Scan & Fill"
        "analyze" -> "Analisis Halaman AI"
        "explain" -> "Penjelasan Lengkap"
        "summary" -> "Ringkas Materi"
        "check" -> "Cek Jawaban"
        "quiz_setup", "quiz_run" -> "Latihan Soal"
        "result" -> resultTitle
        "quiz_result" -> "Hasil Latihan"
        else -> "FRANZZ AI"
    }

    private fun renderPage() {
        val root = panelRoot ?: return
        root.removeAllViews()
        chatLogView = null
        chatInput = null
        sendButton = null
        includeScreenCheck = null
        root.addView(makeHeader(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        val scroll = ScrollView(svc).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(8), 0, dp(4))
        }
        val content = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT))
        bodyScroll = scroll
        body = content
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        when (currentPage) {
            "menu" -> renderMenu(content)
            "study_menu" -> renderStudyMenu(content)
            "chat" -> { renderChat(content); root.addView(makeChatFooter(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)) }
            "fill" -> renderFill(content)
            "analyze" -> renderAnalyze(content)
            "explain" -> renderTextTool(content, "explain")
            "summary" -> renderTextTool(content, "summary")
            "check" -> renderCheck(content)
            "quiz_setup" -> renderQuizSetup(content)
            "quiz_run" -> renderQuizRun(content)
            "result" -> renderResult(content)
            "quiz_result" -> renderQuizResult(content)
            else -> renderMenu(content)
        }
        root.alpha = 0.97f
        root.animate().alpha(1f).setDuration(120).start()
    }

    private fun makeHeader(): View {
        val row = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (backStack.isNotEmpty()) {
            val back = label("‹", 31f, true, muted).apply {
                gravity = Gravity.CENTER
                contentDescription = "Kembali"
                background = ripple(Color.WHITE, line, 10)
                setOnClickListener { goBack() }
            }
            row.addView(back, LinearLayout.LayoutParams(dp(36), dp(42)))
        }
        val icon = ImageView(svc).apply { setImageBitmap(idle); scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "Robot FRANZZ" }
        row.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titleBox = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(6), 0) }
        titleBox.addView(label(titleForPage(), 16f, true, ink))
        titleBox.addView(label("Belajar dan bantu tugas di layar", 10.5f, false, muted))
        row.addView(titleBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val close = label("×", 25f, false, muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Tutup popup"
            background = ripple(Color.WHITE, line, 20)
            setOnClickListener { closePanel() }
        }
        row.addView(close, LinearLayout.LayoutParams(dp(38), dp(38)))
        return row
    }

    private fun renderMenu(content: LinearLayout) {
        content.addView(label("Semua tools AI", 20f, true, ink))
        content.addView(label("Pilih yang kamu perlukan. Popup ini menyatukan semua fitur seperti di web FRANZZ Orbit.", 12.5f, false, muted))
        gap(content, 12)
        toolCard(content, "Scan & Fill", "Siapkan jawaban kolom lalu periksa sebelum diterapkan", android.R.drawable.ic_menu_edit) { goTo("fill") }
        toolCard(content, "Analisis Halaman AI", "Pahami isi layar, tujuan halaman, dan poin penting", android.R.drawable.ic_menu_info_details) { goTo("analyze") }
        toolCard(content, "Fitur AI", "Tutor, penjelasan, ringkasan, cek jawaban, latihan soal", android.R.drawable.ic_menu_agenda) { goTo("study_menu") }
        toolCard(content, "Chat AI", "Tanya bebas; bisa memakai suara dan konteks layar", android.R.drawable.ic_menu_send) { openChat("") }
        gap(content, 8)
        content.addView(note("Teks layar dibaca hanya saat kamu menjalankan fitur terkait. Kolom password diabaikan."))
    }

    private fun renderStudyMenu(content: LinearLayout) {
        content.addView(label("Belajar lebih cepat bersama FRANZZ", 18f, true, ink))
        content.addView(label("Pilih alat belajar. Nilai dari AI adalah perkiraan untuk latihan, bukan nilai resmi.", 12.5f, false, muted))
        gap(content, 12)
        toolCard(content, "Tutor AI", "Dijelaskan bertahap, ada pertanyaan pengecekan", android.R.drawable.ic_menu_help) { openChat("tutor") }
        toolCard(content, "Tanya AI", "Chat bebas, bisa menggunakan suara", android.R.drawable.ic_menu_send) { openChat("") }
        toolCard(content, "Penjelasan lengkap", "Konsep, langkah, contoh, dan kesalahan umum", android.R.drawable.ic_menu_view) { goTo("explain") }
        toolCard(content, "Ringkas Materi", "Poin penting, istilah, dan pertanyaan pengecekan", android.R.drawable.ic_menu_sort_by_size) { goTo("summary") }
        toolCard(content, "Cek Jawaban", "Nilai jawabanmu sendiri dan dapatkan perkiraan skor", android.R.drawable.ic_menu_edit) { goTo("check") }
        toolCard(content, "Latihan Soal", "Buat soal dari materi, jawab, lalu dapatkan penilaian", android.R.drawable.ic_menu_agenda) { goTo("quiz_setup") }
    }

    private fun renderFill(content: LinearLayout) {
        content.addView(label("Siapkan jawaban untuk formulir yang terlihat", 17f, true))
        content.addView(label("FRANZZ membaca label kolom melalui Aksesibilitas, meminta saran ke server, lalu menampilkan pratinjau. Tombol kirim formulir tetap kamu tekan sendiri.", 12.5f, false, muted))
        gap(content, 12)
        val status = label("", 13f, false, muted)
        lateinit var scan: Button
        scan = primaryButton("Pindai layar dan siapkan jawaban") {
            var captured: List<Field> = emptyList()
            var capturedText = ""
            launchTask(scan, status, worker = {
                val screen = ScreenReader.read(svc) ?: throw ApiError(400, "Tidak bisa membaca layar. Pastikan layanan Aksesibilitas Bot FRANZZ aktif.")
                if (screen.fields.isEmpty()) throw ApiError(400, "Tidak ada kolom yang didukung di layar ini.")
                captured = screen.fields
                capturedText = screen.text
                val questions = JSONArray()
                screen.fields.forEach { questions.put(it.toJson()) }
                val response = Api.call(svc, "/api/gemini", JSONObject().put("questions", questions).put("profile", Store.profile(svc)))
                val received = response.optJSONArray("answers") ?: JSONArray()
                val clean = JSONArray()
                val preview = ArrayList<String>()
                for (i in 0 until received.length()) {
                    val a = received.optJSONObject(i) ?: continue
                    if (a.optBoolean("skip", false)) continue
                    val f = screen.fields.firstOrNull { it.id == a.optString("id") } ?: continue
                    if (f.type == "text") {
                        val text = a.optString("text", "").trim().take(500)
                        if (text.isNotEmpty()) {
                            clean.put(JSONObject().put("id", f.id).put("text", text))
                            preview.add("${f.title}: $text")
                        }
                    } else {
                        val choices = a.optJSONArray("choices")
                        val indexes = ArrayList<Int>()
                        if (choices != null) for (j in 0 until choices.length()) {
                            val idx = choices.optInt(j, -1)
                            if (idx in f.options.indices && idx !in indexes) indexes.add(idx)
                        }
                        val chosen = if (f.type == "multi") indexes else indexes.take(1)
                        if (chosen.isNotEmpty()) {
                            clean.put(JSONObject().put("id", f.id).put("choices", JSONArray(chosen)))
                            preview.add("${f.title}: " + chosen.joinToString(", ") { f.options[it] })
                        }
                    }
                }
                if (clean.length() == 0) throw ApiError(422, "AI tidak memberikan jawaban yang cukup yakin. Isi kolom secara manual.")
                JSONObject().put("count", clean.length()).put("total", screen.fields.size)
                    .put("preview", preview.joinToString("\n")).put("answers", clean)
            }, onSuccess = { response ->
                fillFields = captured
                fillAnswers = response.optJSONArray("answers") ?: JSONArray()
                resultKind = "fill"
                resultTitle = "Pratinjau Scan & Fill"
                resultText = "Jawaban untuk ${response.optInt("count")} dari ${response.optInt("total")} kolom:\n\n${response.optString("preview")}\n\nPeriksa kembali jawaban sebelum diterapkan."
                resultData = response
                goTo("result")
            })
        }
        content.addView(scan, matchParams().apply { topMargin = dp(12) })
        content.addView(status)
    }

    private fun renderAnalyze(content: LinearLayout) {
        content.addView(label("Analisis isi layar", 18f, true))
        content.addView(label("Jalankan ini pada artikel, petunjuk, halaman, atau materi. Jika layar berisi ujian/kuis bernilai, AI hanya akan menjelaskan topiknya secara umum.", 12.5f, false, muted))
        gap(content, 12)
        val instruction = edit("Apa yang ingin kamu pahami dari halaman ini? (opsional)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3)
        content.addView(instruction, matchParams())
        val status = label("", 13f, false, muted)
        lateinit var analyze: Button
        analyze = primaryButton("Analisis halaman") {
            val instructionText = instruction.text.toString().trim()
            launchTask(analyze, status, worker = {
                val screen = ScreenReader.read(svc) ?: throw ApiError(400, "Tidak bisa membaca teks layar. Pastikan Aksesibilitas Bot FRANZZ aktif dan aplikasi tujuan mengekspos teksnya.")
                if (screen.text.trim().length < 20) throw ApiError(400, "Teks yang dapat dibaca terlalu sedikit. Coba halaman atau aplikasi lain.")
                Api.call(svc, "/api/analyze", JSONObject()
                    .put("title", "Layar aplikasi ${screen.pkg}")
                    .put("url", screen.pkg)
                    .put("text", screen.text.take(30000))
                    .put("instruction", instructionText.ifBlank { "Ringkas halaman ini, sebutkan poin penting, dan jelaskan apa yang perlu diperhatikan." }))
            }, onSuccess = { response ->
                resultKind = "text"; resultTitle = "Hasil Analisis AI"; resultText = response.optString("text", "Tidak ada jawaban dari server."); resultData = null
                goTo("result")
            })
        }
        content.addView(analyze, matchParams().apply { topMargin = dp(12) })
        content.addView(status)
    }

    private fun renderTextTool(content: LinearLayout, mode: String) {
        val explain = mode == "explain"
        val input = edit(if (explain) "Tulis topik atau pertanyaan yang ingin dijelaskan…" else "Tempel materi di sini, atau gunakan teks layar yang sedang terbuka…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 5)
        content.addView(label(if (explain) "Topik atau pertanyaan" else "Materi", 13f, true))
        content.addView(input, matchParams())
        val cb = checkbox("Gunakan konteks layar yang sedang terbuka", false)
        content.addView(cb)
        val status = label("", 13f, false, muted)
        lateinit var go: Button
        go = primaryButton(if (explain) "Jelaskan lengkap" else "Ringkas materi") {
            val value = input.text.toString().trim()
            val useScreen = cb.isChecked
            launchTask(go, status, worker = {
                var page: JSONObject? = null
                if (useScreen) {
                    val screen = ScreenReader.read(svc) ?: throw ApiError(400, "Layanan tidak bisa membaca konteks layar.")
                    if (screen.text.isNotBlank()) page = pageJson(screen)
                }
                if (value.length < 3 && page == null) throw ApiError(400, "Tulis topik atau aktifkan konteks layar dulu.")
                if (!explain && value.length < 80 && (page == null || page.optString("text").length < 80)) {
                    throw ApiError(400, "Materi terlalu pendek. Tempel beberapa kalimat atau gunakan halaman materi.")
                }
                val message = when {
                    explain && value.isNotBlank() -> "Jelaskan topik/pertanyaan ini secara lengkap: $value"
                    explain -> "Jelaskan isi halaman ini secara lengkap."
                    else -> "Ringkas materi ini."
                }
                val messages = JSONArray().put(JSONObject().put("role", "user").put("text", message))
                val request = JSONObject().put("messages", messages).put("mode", mode)
                if (page != null) request.put("page", page)
                else if (!explain && value.length >= 80) request.put("page", JSONObject().put("title", "Materi yang ditempel").put("url", "").put("text", value.take(20000)))
                Api.call(svc, "/api/chat", request)
            }, onSuccess = { response ->
                resultKind = "text"; resultTitle = if (explain) "Penjelasan Lengkap" else "Ringkasan Materi"; resultText = response.optString("text", "Tidak ada jawaban dari server."); resultData = JSONObject().put("mode", mode).put("prompt", valueForContinuation(value, explain))
                goTo("result")
            })
        }
        content.addView(go, matchParams().apply { topMargin = dp(12) })
        content.addView(status)
    }

    private fun valueForContinuation(value: String, explain: Boolean): String = when {
        value.isNotBlank() -> if (explain) "Jelaskan topik: $value" else "Ringkas materi ini."
        explain -> "Jelaskan isi halaman ini secara lengkap."
        else -> "Ringkas materi ini."
    }

    private fun renderCheck(content: LinearLayout) {
        content.addView(note("Cek Jawaban menilai jawabanmu sendiri, bukan membuat jawaban untuk disalin. Skor adalah perkiraan untuk latihan."))
        val question = edit("Tempel atau tulis soalnya…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3)
        val answer = edit("Tulis jawabanmu sendiri di sini…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 5)
        val key = edit("Kunci atau rubrik (opsional)…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 2)
        content.addView(label("Soal", 13f, true)); content.addView(question, matchParams())
        gap(content, 8)
        content.addView(label("Jawabanku", 13f, true)); content.addView(answer, matchParams())
        gap(content, 8)
        content.addView(label("Kunci / rubrik (opsional)", 13f, true)); content.addView(key, matchParams())
        val status = label("", 13f, false, muted)
        lateinit var go: Button
        go = primaryButton("Cek jawabanku") {
            val questionText = question.text.toString()
            val answerText = answer.text.toString()
            val keyText = key.text.toString()
            launchTask(go, status, worker = {
                Api.call(svc, "/api/study", JSONObject().put("tool", "check").put("question", questionText).put("answer", answerText).put("key", keyText))
            }, onSuccess = { response -> resultKind = "check"; resultTitle = "Hasil Cek Jawaban"; resultData = response; resultText = ""; goTo("result") })
        }
        content.addView(go, matchParams().apply { topMargin = dp(12) }); content.addView(status)
    }

    private fun renderQuizSetup(content: LinearLayout) {
        content.addView(label("Buat latihan dari materi", 18f, true))
        content.addView(label("Tempel materi yang cukup panjang atau pilih untuk membaca teks layar. Soal yang dibuat hanya untuk latihan belajar.", 12.5f, false, muted))
        gap(content, 10)
        val material = edit("Tempel materi pelajaran di sini…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 5)
        content.addView(material, matchParams())
        val cb = checkbox("Gunakan teks layar sebagai materi bila isian di atas pendek", false)
        content.addView(cb)
        val optionsRow = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        val countSpinner = Spinner(svc).apply {
            adapter = ArrayAdapter(svc, android.R.layout.simple_spinner_dropdown_item, listOf("3", "5", "8"))
            setSelection(1)
        }
        val kindSpinner = Spinner(svc).apply {
            adapter = ArrayAdapter(svc, android.R.layout.simple_spinner_dropdown_item, listOf("Campur", "Pilihan ganda", "Esai singkat"))
            setSelection(0)
        }
        val col1 = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        col1.addView(label("Jumlah soal", 12f, true)); col1.addView(countSpinner)
        val col2 = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        col2.addView(label("Jenis soal", 12f, true)); col2.addView(kindSpinner)
        optionsRow.addView(col1, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        optionsRow.addView(col2, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(optionsRow)
        val status = label("", 13f, false, muted)
        lateinit var go: Button
        go = primaryButton("Buat soal") {
            val own = material.text.toString().trim()
            val selectedCount = countSpinner.selectedItem.toString().toIntOrNull() ?: 5
            val selectedKind = when (kindSpinner.selectedItemPosition) { 1 -> "pilgan"; 2 -> "esai"; else -> "campur" }
            launchTask(go, status, worker = {
                val request = JSONObject().put("tool", "quiz").put("count", selectedCount).put("kind", selectedKind)
                var page: JSONObject? = null
                if (own.length >= 80) request.put("material", own.take(20000))
                else if (cb.isChecked) {
                    val screen = ScreenReader.read(svc) ?: throw ApiError(400, "Tidak bisa membaca materi pada layar.")
                    if (screen.text.trim().length < 80) throw ApiError(400, "Teks layar terlalu pendek. Tempel materi secara manual.")
                    page = pageJson(screen)
                    request.put("page", page)
                } else throw ApiError(400, "Tempel materi (minimal 80 karakter) atau aktifkan pilihan konteks layar.")
                Api.call(svc, "/api/study", request)
            }, onSuccess = { response ->
                val array = response.optJSONArray("questions") ?: JSONArray()
                val qs = ArrayList<QuizQuestion>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val optsArray = obj.optJSONArray("options")
                    val opts = ArrayList<String>()
                    if (optsArray != null) for (j in 0 until optsArray.length()) opts.add(optsArray.optString(j))
                    qs.add(QuizQuestion(obj.optString("type"), obj.optString("q"), opts, obj.optInt("ref", -1), obj.optString("why")))
                }
                if (qs.isEmpty()) { status.text = "AI belum berhasil membuat soal. Coba lagi."; status.setTextColor(error) }
                else { quizQuestions = qs; quizAnswers.clear(); repeat(qs.size) { quizAnswers.add(null) }; quizCount = selectedCount; quizKind = selectedKind; goTo("quiz_run") }
            })
        }
        content.addView(go, matchParams().apply { topMargin = dp(12) }); content.addView(status)
    }

    private fun renderQuizRun(content: LinearLayout) {
        content.addView(note("Jawab pertanyaan di bawah, lalu kirim untuk mendapatkan nilai dan umpan balik."))
        quizQuestions.forEachIndexed { index, q ->
            val box = cardBox()
            box.addView(label("${index + 1}. ${q.question}", 14f, true))
            if (q.type == "pilgan") {
                val group = RadioGroup(svc).apply { orientation = RadioGroup.VERTICAL }
                q.options.forEachIndexed { optionIndex, option ->
                    val radio = RadioButton(svc).apply {
                        text = "${'A' + optionIndex}. $option"
                        textSize = 13f
                        setTextColor(ink)
                        buttonTintList = ColorStateList.valueOf(accent)
                        setPadding(dp(2), dp(4), dp(2), dp(4))
                        id = View.generateViewId()
                        setOnClickListener { quizAnswers[index] = optionIndex }
                    }
                    group.addView(radio)
                }
                box.addView(group)
            } else {
                val essay = edit("Tulis jawabanmu…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3)
                essay.tag = index
                box.addView(essay, matchParams())
                essay.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { quizAnswers[index] = s?.toString().orEmpty() }
                    override fun afterTextChanged(s: android.text.Editable?) { }
                })
            }
            content.addView(box, matchParams().apply { topMargin = dp(8) })
        }
        val status = label("", 13f, false, muted)
        lateinit var grade: Button
        grade = primaryButton("Nilai jawabanku") {
            launchTask(grade, status, worker = {
                val items = JSONArray()
                quizQuestions.forEachIndexed { index, q ->
                    val answer = quizAnswers.getOrNull(index)
                    val item = JSONObject().put("type", q.type).put("q", q.question).put("options", JSONArray(q.options))
                        .put("ref", q.ref).put("why", q.why)
                    when (answer) {
                        null -> item.put("answer", JSONObject.NULL)
                        is Int -> item.put("answer", answer)
                        else -> item.put("answer", answer.toString())
                    }
                    items.put(item)
                }
                Api.call(svc, "/api/study", JSONObject().put("tool", "grade").put("items", items))
            }, onSuccess = { response -> resultData = response; resultKind = "quiz-grade"; resultTitle = "Hasil Latihan"; goTo("quiz_result") })
        }
        content.addView(grade, matchParams().apply { topMargin = dp(12) })
        content.addView(button("Ganti materi", false) { goBack() }, matchParams().apply { topMargin = dp(8) })
        content.addView(status)
    }

    private fun renderResult(content: LinearLayout) {
        when (resultKind) {
            "check" -> renderCheckResult(content, resultData ?: JSONObject())
            "fill" -> {
                content.addView(note("Pratinjau jawaban. Periksa tiap isian sebelum menerapkannya."))
                val result = label(resultText, 13.5f, false, ink)
                result.setTextIsSelectable(true)
                content.addView(result, matchParams())
                content.addView(primaryButton("Terapkan ke layar") {
                    val filled = ScreenReader.apply(fillFields, fillAnswers)
                    resultText += "\n\nBerhasil menerapkan ke $filled kolom. Periksa hasil di aplikasi asal dan tekan tombol kirim sendiri."
                    result.text = resultText
                    result.setTextColor(success)
                }, matchParams().apply { topMargin = dp(12) })
            }
            else -> {
                val result = label(resultText, 14f, false, ink)
                result.setTextIsSelectable(true)
                result.setLineSpacing(dp(4).toFloat(), 1.05f)
                content.addView(result, matchParams())
                if (resultData?.optString("mode").orEmpty().isNotBlank()) {
                    content.addView(button("Lanjut tanya di Chat AI", false) {
                        val prompt = resultData?.optString("prompt").orEmpty()
                        chatMode = resultData?.optString("mode").orEmpty()
                        chat.clear()
                        if (prompt.isNotBlank()) chat.add(ChatLine("user", prompt))
                        chat.add(ChatLine("assistant", resultText))
                        goTo("chat")
                    }, matchParams().apply { topMargin = dp(14) })
                }
            }
        }
        content.addView(button("Kembali ke menu AI", false) {
            backStack.clear(); currentPage = "menu"; renderPage()
        }, matchParams().apply { topMargin = dp(14) })
    }

    private fun renderCheckResult(content: LinearLayout, data: JSONObject) {
        val score = data.optInt("score", 0).coerceIn(0, 100)
        val scoreCard = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFECE7FF.toInt(), 0xFFF7F4FF.toInt())).apply {
                cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFFD4C9FF.toInt())
            }
        }
        scoreCard.addView(label("Perkiraan Skor", 13f, true, muted))
        scoreCard.addView(label("$score/100", 32f, true, accent))
        scoreCard.addView(label(data.optString("verdict"), 13f, false, ink))
        content.addView(scoreCard, matchParams())
        addStringArraySection(content, "Yang sudah baik", data.optJSONArray("strengths"), success)
        addStringArraySection(content, "Yang masih kurang", data.optJSONArray("gaps"), error)
        addStringArraySection(content, "Saran", data.optJSONArray("tips"), accent)
        val correction = data.optString("correction", "")
        if (correction.isNotBlank()) {
            gap(content, 8)
            content.addView(label("Inti jawaban yang benar", 14f, true))
            content.addView(label(correction, 13.5f, false, ink))
        }
        content.addView(note("Skor ini merupakan perkiraan AI untuk latihan belajar, bukan nilai resmi."))
    }

    private fun renderQuizResult(content: LinearLayout) {
        val data = resultData ?: JSONObject()
        val total = data.optInt("total", 0).coerceIn(0, 100)
        content.addView(label("Skor Latihan", 14f, true, muted))
        content.addView(label("$total/100", 32f, true, accent))
        val items = data.optJSONArray("items") ?: JSONArray()
        quizQuestions.forEachIndexed { index, q ->
            val item = items.optJSONObject(index) ?: JSONObject()
            val box = cardBox()
            box.addView(label("${index + 1}. ${q.question}", 14f, true))
            if (q.type == "pilgan") {
                val correct = item.optBoolean("correct", false)
                box.addView(label(if (correct) "Jawaban benar" else "Jawaban belum tepat", 13f, true, if (correct) success else error))
                val chosen = item.optString("chosen", "")
                if (chosen.isNotBlank()) box.addView(label("Jawabanmu: $chosen", 12.5f, false, muted))
                if (!correct) box.addView(label("Jawaban benar: ${item.optString("ref")}", 12.5f, false, ink))
            } else {
                box.addView(label("Perkiraan nilai: ${item.optInt("score", 0)}/100", 13f, true, accent))
                if (item.optString("feedback").isNotBlank()) box.addView(label(item.optString("feedback"), 12.5f, false, ink))
                if (item.optString("ref").isNotBlank()) box.addView(label("Poin acuan: ${item.optString("ref")}", 12f, false, muted))
            }
            content.addView(box, matchParams().apply { topMargin = dp(8) })
        }
        content.addView(note("Skor latihan merupakan perkiraan untuk membantu belajar, bukan nilai resmi."))
        content.addView(primaryButton("Latihan lagi") { goTo("quiz_setup") }, matchParams().apply { topMargin = dp(10) })
        content.addView(button("Kembali ke menu AI", false) { backStack.clear(); currentPage = "menu"; renderPage() }, matchParams().apply { topMargin = dp(8) })
    }

    private fun addStringArraySection(parent: LinearLayout, title: String, values: JSONArray?, color: Int) {
        if (values == null || values.length() == 0) return
        gap(parent, 8)
        parent.addView(label(title, 14f, true, color))
        for (i in 0 until values.length()) parent.addView(label("• ${values.optString(i)}", 13f, false, ink))
    }

    private fun renderChat(content: LinearLayout) {
        content.addView(label(if (chatMode == "tutor") "Belajar bertahap dengan Tutor AI" else "Tanya apa saja", 17f, true))
        content.addView(label(if (chatMode == "tutor") "Aku akan memberi petunjuk, contoh, lalu pertanyaan pengecekan." else "Teks layar hanya dikirim jika kamu mengaktifkan pilihan konteks layar di bawah.", 12.5f, false, muted))
        gap(content, 8)
        chatLogView = content
        if (chat.isEmpty()) addChatMessage(if (chatMode == "tutor") "Tulis topik atau soal yang sedang dipelajari. Kita bahas bertahap." else "Tulis pertanyaanmu di kolom pesan. Kamu juga bisa bertanya dengan suara.", false, false)
        else chat.forEach { addChatMessage(it.text, it.role == "user", false) }
    }

    private fun makeChatFooter(): View {
        val footer = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        val cb = checkbox("Sertakan teks layar yang sedang terbuka", includeScreen)
        cb.setOnCheckedChangeListener { _, checked -> includeScreen = checked }
        includeScreenCheck = cb
        footer.addView(cb)
        val input = EditText(svc).apply {
            hint = "Tulis tugas atau pertanyaan di sini…"
            textSize = 15.5f
            setTextColor(ink)
            setHintTextColor(0xFF9892B0.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; maxLines = 4; isSingleLine = false
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.WHITE, 0xFFFBFAFF.toInt())).apply {
                cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFFB8A9F9.toInt())
            }
        }
        chatInput = input
        footer.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { minHeight = dp(74) })
        val buttons = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val voice = button("Suara", false) { startVoiceInput() }
        buttons.addView(voice, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        val read = button("Bacakan", false) {
            if (lastAnswer.isBlank()) Toast.makeText(svc, "Belum ada jawaban untuk dibacakan.", Toast.LENGTH_SHORT).show() else speak(lastAnswer)
        }
        buttons.addView(read, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        val send = primaryButton("Kirim") { sendChat() }
        sendButton = send
        buttons.addView(send, LinearLayout.LayoutParams(0, dp(46), 1f))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(7)
        footer.addView(buttons, lp)
        val utilities = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        utilities.addView(button("Isi kolom di layar", false) { goTo("fill") }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        utilities.addView(button("Hapus chat", false) {
            chat.clear(); lastAnswer = ""; if (currentPage == "chat") renderPage()
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        footer.addView(utilities, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        return footer
    }

    private fun sendChat() {
        val input = chatInput ?: return
        val text = input.text.toString().trim()
        if (text.isBlank() || busy) return
        chat.add(ChatLine("user", text))
        addChatMessage(text, true, false)
        input.setText("")
        val waiting = addChatMessage("Menyiapkan jawaban…", false, false)
        val shouldUseScreen = includeScreenCheck?.isChecked == true
        val messages = JSONArray()
        chat.takeLast(12).forEach { messages.put(JSONObject().put("role", it.role).put("text", it.text)) }
        val request = JSONObject().put("messages", messages).put("mode", chatMode)
        busy = true
        sendButton?.isEnabled = false
        Thread {
            try {
                if (shouldUseScreen) {
                    val screen = ScreenReader.read(svc)
                    if (screen != null && screen.text.isNotBlank()) request.put("page", pageJson(screen))
                }
                val response = Api.call(svc, "/api/chat", request)
                val answer = response.optString("text", "").ifBlank { "Server tidak mengirim jawaban teks." }
                ui.post {
                    chat.add(ChatLine("assistant", answer))
                    lastAnswer = answer
                    if (waiting != null) { waiting.text = answer; styleMessage(waiting, false, false) }
                    if (speakNextReply) speak(answer)
                    speakNextReply = false
                    scrollDown()
                    busy = false; sendButton?.isEnabled = true
                }
            } catch (e: Exception) {
                ui.post {
                    if (chat.isNotEmpty() && chat.last().role == "user" && chat.last().text == text) chat.removeAt(chat.lastIndex)
                    if (waiting != null) { waiting.text = Api.describe(e); styleMessage(waiting, false, true) }
                    speakNextReply = false
                    busy = false; sendButton?.isEnabled = true
                    scrollDown()
                }
            }
        }.start()
    }

    private fun addChatMessage(text: String, mine: Boolean, isError: Boolean): TextView? {
        val container = chatLogView ?: body ?: return null
        val bubble = label(text, 13.5f, false, if (mine) Color.WHITE else if (isError) error else ink)
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9))
        bubble.background = rounded(if (mine) accent else if (isError) 0xFFFFF0EF.toInt() else Color.WHITE, if (mine) accent else line, 14)
        bubble.setTextIsSelectable(true)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(4); lp.bottomMargin = dp(4)
        lp.gravity = if (mine) Gravity.END else Gravity.START
        lp.width = (svc.resources.displayMetrics.widthPixels * 0.70f).toInt().coerceAtMost(dp(350))
        container.addView(bubble, lp)
        scrollDown()
        return bubble
    }

    private fun styleMessage(view: TextView, mine: Boolean, isError: Boolean) {
        view.setTextColor(if (mine) Color.WHITE else if (isError) error else ink)
        view.background = rounded(if (mine) accent else if (isError) 0xFFFFF0EF.toInt() else Color.WHITE, if (mine) accent else line, 14)
    }

    private fun scrollDown() { bodyScroll?.post { bodyScroll?.fullScroll(View.FOCUS_DOWN) } }

    private fun startVoiceInput() {
        if (busy) { addChatMessage("Tunggu jawaban sebelumnya selesai dulu.", false, false); return }
        if (svc.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            addChatMessage("Izin mikrofon diperlukan. Buka aplikasi FRANZZ Orbit dan aktifkan izin mikrofon.", false, true)
            try { svc.startActivity(Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("requestAudioPermission", true)) }
            catch (_: Exception) { }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(svc)) {
            addChatMessage("Pengenalan suara tidak tersedia di perangkat ini. Coba aktifkan layanan speech recognition.", false, true)
            return
        }
        stopVoice()
        speakNextReply = true
        val prompt = addChatMessage("Mendengarkan…", false, false)
        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(svc)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { ui.post { prompt?.text = "Silakan bicara…" } }
                override fun onBeginningOfSpeech() { }
                override fun onRmsChanged(rmsdB: Float) { }
                override fun onBufferReceived(buffer: ByteArray?) { }
                override fun onEndOfSpeech() { ui.post { prompt?.text = "Memproses ucapan…" } }
                override fun onError(errorCode: Int) {
                    ui.post { speakNextReply = false; prompt?.text = "Ucapan belum tertangkap. Tekan Suara lagi dan coba bicara lebih dekat."; prompt?.setTextColor(error); stopVoice() }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    ui.post {
                        stopVoice()
                        if (spoken.isBlank()) { speakNextReply = false; prompt?.text = "Ucapan tidak terdeteksi. Coba lagi."; return@post }
                        prompt?.text = "Ucapan: $spoken"
                        chatInput?.setText(spoken)
                        chatInput?.setSelection(chatInput?.text?.length ?: 0)
                        sendChat()
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
        } catch (_: Exception) {
            speakNextReply = false
            stopVoice()
            prompt?.text = "Tidak bisa memulai mikrofon. Periksa izin atau layanan pengenalan suara."
            prompt?.setTextColor(error)
        }
    }

    private fun stopVoice() {
        val old = recognizer
        recognizer = null
        try { old?.cancel(); old?.destroy() } catch (_: Exception) { }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        if (tts == null) tts = TextToSpeech(svc) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("id", "ID")
                ttsReady = true
                pendingSpeech?.let { pending -> pendingSpeech = null; tts?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "franzz-answer") }
            }
        }
        if (!ttsReady) { pendingSpeech = text; return }
        try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "franzz-answer") }
        catch (_: Exception) { Toast.makeText(svc, "Fitur pembacaan suara tidak tersedia.", Toast.LENGTH_SHORT).show() }
    }

    private fun launchTask(button: Button, status: TextView, worker: () -> JSONObject, onSuccess: (JSONObject) -> Unit) {
        if (busy) { status.text = "Tunggu proses sebelumnya selesai."; status.setTextColor(muted); return }
        busy = true
        button.isEnabled = false
        status.text = "Memproses…"
        status.setTextColor(muted)
        val pageAtStart = currentPage
        Thread {
            var result: JSONObject? = null
            var errorMessage: String? = null
            try { result = worker() } catch (e: Exception) { errorMessage = Api.describe(e) }
            ui.post {
                busy = false
                button.isEnabled = true
                if (panelRoot == null || currentPage != pageAtStart) return@post
                if (errorMessage != null) { status.text = errorMessage; status.setTextColor(error) }
                else {
                    status.text = "Selesai."
                    status.setTextColor(success)
                    result?.let(onSuccess)
                }
            }
        }.start()
    }

    private fun screenContext(): Screen? = try { ScreenReader.read(svc) } catch (_: Exception) { null }
    private fun pageJson(screen: Screen): JSONObject {
        val text = StringBuilder(screen.text)
        if (screen.fields.isNotEmpty()) {
            text.append("\n\nKolom isian di layar:\n")
            screen.fields.forEachIndexed { i, field ->
                text.append("${i + 1}. [${field.type}] ${field.title}")
                if (field.options.isNotEmpty()) text.append(" | pilihan: ").append(field.options.joinToString(" / "))
                text.append('\n')
            }
        }
        return JSONObject().put("url", screen.pkg).put("title", "Layar aplikasi ${screen.pkg}").put("text", text.toString().take(30000))
    }

    private fun label(text: String, size: Float, bold: Boolean = false, color: Int = ink): TextView = TextView(svc).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun rounded(fill: Int, stroke: Int = 0, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun ripple(fill: Int, rippleColor: Int, radius: Int): RippleDrawable = RippleDrawable(
        ColorStateList.valueOf(rippleColor), rounded(fill, 0, radius), null
    )

    private fun button(text: String, primary: Boolean = false, action: () -> Unit): Button = Button(svc).apply {
        this.text = text
        isAllCaps = false
        textSize = 12.5f
        setTextColor(if (primary) Color.WHITE else ink)
        background = ripple(if (primary) accent else Color.WHITE, if (primary) accent2 else line, 13)
        minHeight = dp(42); minimumHeight = dp(42)
        setPadding(dp(10), dp(5), dp(10), dp(5))
        setOnClickListener { action() }
    }

    private fun primaryButton(text: String, action: () -> Unit): Button = button(text, true, action)
    private fun checkbox(text: String, checked: Boolean): CheckBox = CheckBox(svc).apply {
        this.text = text
        textSize = 12f
        setTextColor(muted)
        buttonTintList = ColorStateList.valueOf(accent)
        isChecked = checked
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun note(text: String): TextView = label(text, 12f, false, muted).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(0xFFF0ECFF.toInt(), 0xFFE2D9FF.toInt(), 13)
    }

    private fun cardBox(): LinearLayout = LinearLayout(svc).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(11), dp(13), dp(11))
        background = rounded(Color.WHITE, line, 15)
    }

    private fun matchParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(svc), LinearLayout.LayoutParams(1, dp(height))) }

    private fun toolCard(parent: LinearLayout, title: String, subtitle: String, iconRes: Int, action: () -> Unit) {
        val row = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(10), dp(11))
            background = ripple(Color.WHITE, 0xFFDAD1FF.toInt(), 16)
            elevation = dp(1).toFloat()
        }
        val icon = ImageView(svc).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(accent)
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = rounded(0xFFF0ECFF.toInt(), 0, 13)
            contentDescription = title
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val textBox = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, dp(4), 0) }
        textBox.addView(label(title, 14f, true, ink))
        textBox.addView(label(subtitle, 11f, false, muted))
        row.addView(textBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 24f, false, muted).apply { gravity = Gravity.CENTER })
        row.setOnClickListener {
            row.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).withEndAction {
                row.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                action()
            }.start()
        }
        parent.addView(row, matchParams().apply { bottomMargin = dp(8) })
    }
}
