package com.franzz.orbit

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val PURPLE = 0xFF7B3FE4.toInt()
    private val BLUE = 0xFF2F6BFF.toInt()
    private val INK = 0xFF1B1640.toInt()
    private val GREY = 0xFF9AA0B4.toInt()
    private val BUSY = 0xFFE0A100.toInt()
    private val OK = 0xFF1F9D63.toInt()
    private val ERR = 0xFFD9480F.toInt()

    private class Plan(val id: String, val kind: String, val choices: List<Int>, val text: String?, val title: String, val summary: String)

    private lateinit var store: SecureStore
    private lateinit var scannerJs: String
    private lateinit var web: WebView
    private lateinit var urlBox: EditText
    private lateinit var stage: FrameLayout
    private lateinit var bubble: FrameLayout
    private lateinit var dot: View
    private lateinit var panel: LinearLayout
    private lateinit var statusTv: TextView
    private lateinit var opLabel: TextView
    private lateinit var goBtn: Button
    private lateinit var stopBtn: Button
    private val panelBg = GradientDrawable()
    private val fadeViews = mutableListOf<View>()
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var running = false
    private var stopped = false
    private var runId = 0
    private var pendingAuto = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun grad(r: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(PURPLE, BLUE)).apply { cornerRadius = r }

    private fun btn(label: String, bg: Int? = null) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); minHeight = 0; minimumHeight = 0
        background = if (bg == null) grad(dp(10).toFloat()) else GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(bg) }
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        scannerJs = assets.open("scanner.js").bufferedReader().use { it.readText() }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }

        // Bar link
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        urlBox = EditText(this).apply {
            hint = "Tempel link form di sini"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> go(); true }
        }
        val autoBtn = btn("Isi otomatis").apply { setOnClickListener { go() } }
        val setBtn = btn("⚙", INK).apply { setOnClickListener { showSettings() } }
        bar.addView(urlBox, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(autoBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(6) })
        bar.addView(setBtn, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { leftMargin = dp(6) })
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // WebView
        stage = FrameLayout(this)
        web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            setSupportMultipleWindows(false)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme; return s != "http" && s != "https"
            }
            override fun onPageFinished(v: WebView, url: String) {
                if (pendingAuto) { pendingAuto = false; ui.postDelayed({ startRun() }, 1200) }
            }
        }
        stage.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Panel
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; elevation = dp(8).toFloat(); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        panelBg.cornerRadius = dp(16).toFloat(); panelBg.setStroke(dp(2), INK); panel.background = panelBg
        statusTv = TextView(this).apply { text = "Siap"; setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, 0, dp(8)) }
        goBtn = btn("Scan & Fill").apply { setOnClickListener { startRun() } }
        stopBtn = btn("STOP", ERR).apply { visibility = View.GONE; setOnClickListener { stopped = true; finish(runId, "Dihentikan.", ERR) } }
        opLabel = TextView(this).apply { setTextColor(INK); setPadding(0, dp(10), 0, 0) }
        val seek = SeekBar(this).apply { max = 90; progress = store.opacity - 10 }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { applyOpacity(p + 10) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { store.opacity = seek.progress + 10 }
        })
        listOf(statusTv, goBtn, stopBtn, opLabel, seek).forEach { panel.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }
        fadeViews.addAll(listOf(statusTv, goBtn, stopBtn))
        stage.addView(panel, FrameLayout.LayoutParams(dp(250), WRAP_CONTENT))

        // Robot bubble
        val size = dp(56)
        bubble = FrameLayout(this).apply { elevation = dp(6).toFloat(); contentDescription = "Asisten robot FRANZZ Orbit" }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_robot); setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(PURPLE, BLUE)).apply { shape = GradientDrawable.OVAL; setStroke(dp(2), INK) }
        }
        dot = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GREY); setStroke(dp(2), Color.WHITE) } }
        bubble.addView(icon, FrameLayout.LayoutParams(size, size))
        bubble.addView(dot, FrameLayout.LayoutParams(dp(14), dp(14), Gravity.TOP or Gravity.END))
        stage.addView(bubble, FrameLayout.LayoutParams(size, size))

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var ox = 0f; var oy = 0f; var drag = false
        bubble.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = v.x; oy = v.y; drag = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!drag && Math.hypot(dx.toDouble(), dy.toDouble()) > slop) drag = true
                    if (drag) moveBubble(ox + dx, oy + dy)
                }
                MotionEvent.ACTION_UP -> {
                    if (drag) { store.bx = v.x; store.by = v.y } else togglePanel()
                }
            }
            true
        }
        root.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)

        applyOpacity(store.opacity)
        stage.post {
            val x = if (store.bx < 0) (stage.width - size - dp(12)).toFloat() else store.bx
            val y = if (store.by < 0) (stage.height - size - dp(24)).toFloat() else store.by
            moveBubble(x, y)
        }
        if (!store.hasKey()) showSettings()
    }

    // ---------- overlay ----------
    private fun applyOpacity(p: Int) {
        val a = p / 100f
        bubble.alpha = a
        fadeViews.forEach { it.alpha = a }
        panelBg.setColor(Color.argb((a * 255).toInt(), 255, 255, 255))
        opLabel.text = "Opasitas $p%"
    }
    private fun moveBubble(x: Float, y: Float) {
        val s = dp(56)
        bubble.x = x.coerceIn(0f, maxOf(0f, (stage.width - s).toFloat()))
        bubble.y = y.coerceIn(0f, maxOf(0f, (stage.height - s).toFloat()))
        placePanel()
    }
    private fun togglePanel() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        placePanel()
    }
    private fun showPanel() { panel.visibility = View.VISIBLE; placePanel() }
    private fun placePanel() {
        if (panel.visibility != View.VISIBLE) return
        val w = dp(250); val s = dp(56)
        panel.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = panel.measuredHeight
        val minX = dp(8).toFloat(); val maxX = maxOf(minX, (stage.width - w - dp(8)).toFloat())
        panel.x = (bubble.x + s - w).coerceIn(minX, maxX)
        var y = bubble.y - h - dp(8)
        if (y < dp(8)) y = bubble.y + s + dp(8)
        panel.y = y
    }
    private fun setState(msg: String, color: Int) {
        statusTv.text = msg
        (dot.background as GradientDrawable).setColor(color)
        placePanel()
    }

    // ---------- alur utama ----------
    private fun go() {
        var u = urlBox.text.toString().trim()
        if (u.isEmpty()) { toast("Tempel link form dulu."); return }
        if (!u.contains("://")) u = "https://$u"
        val scheme = try { java.net.URI(u).scheme } catch (e: Exception) { null }
        if (scheme != "http" && scheme != "https") { toast("Link tidak valid. Harus diawali http:// atau https://"); return }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(urlBox.windowToken, 0)
        if (running) return
        pendingAuto = true
        setState("Membuka link…", BUSY); showPanel()
        web.loadUrl(u)
    }

    private fun startRun() {
        if (running) return
        if (!store.hasKey()) { setState("Isi API key dulu.", ERR); showPanel(); showSettings(); return }
        running = true; stopped = false
        val my = ++runId
        goBtn.visibility = View.GONE; stopBtn.visibility = View.VISIBLE
        showPanel()
        scan(my, 0)
    }

    private fun finish(my: Int, msg: String, color: Int) {
        if (my != runId) return
        running = false
        goBtn.visibility = View.VISIBLE; stopBtn.visibility = View.GONE
        setState(msg, color); showPanel()
    }

    private fun unwrap(raw: String?): String? = try {
        if (raw == null || raw == "null") null else JSONTokener(raw).nextValue() as? String
    } catch (e: Exception) { null }

    private fun scan(my: Int, attempt: Int) {
        if (my != runId || stopped) return
        setState("Memindai…", BUSY)
        web.evaluateJavascript(scannerJs, null)
        web.evaluateJavascript("__orbit.scan()") { raw ->
            if (my != runId || stopped) return@evaluateJavascript
            val obj = try { JSONObject(unwrap(raw) ?: "") } catch (e: Exception) { null }
            val qs = obj?.optJSONArray("questions")
            if (obj == null || qs == null) { finish(my, "Gagal memindai halaman.", ERR); return@evaluateJavascript }
            if (qs.length() == 0) {
                if (attempt < 3) ui.postDelayed({ scan(my, attempt + 1) }, 1200)
                else finish(my, "Tidak ada soal yang didukung di halaman ini.", ERR)
                return@evaluateJavascript
            }
            val key = store.apiKey()
            if (key == null) { finish(my, "API key tidak terbaca. Isi ulang di Pengaturan.", ERR); return@evaluateJavascript }
            setState("Menganalisis ${qs.length()} soal…", BUSY)
            val unsupported = obj.optInt("unsupported", 0)
            val model = store.model; val profile = store.profile
            io.execute {
                val res = GeminiClient.answer(key, model, profile, qs)
                ui.post { if (my == runId && !stopped) onAnswers(my, qs, unsupported, res) }
            }
        }
    }

    private fun onAnswers(my: Int, qs: JSONArray, unsupported: Int, res: Result<JSONArray>) {
        val ans = res.getOrElse { finish(my, it.message ?: "Gagal memanggil Gemini.", ERR); return }
        setState("Memvalidasi…", BUSY)
        val plans = buildPlans(qs, ans)
        val skipped = qs.length() - plans.size + unsupported
        if (plans.isEmpty()) { finish(my, "Tidak ada jawaban yang cukup yakin. Isi manual.", ERR); return }
        if (store.preview) {
            AlertDialog.Builder(this).setTitle("Jawaban yang akan diisi")
                .setMessage(plans.joinToString("\n\n") { "${it.title}\n→ ${it.summary}" })
                .setPositiveButton("Isi") { _, _ -> fill(my, plans, 0, 0, 0, skipped) }
                .setNegativeButton("Batal") { _, _ -> finish(my, "Dibatalkan.", ERR) }
                .setOnCancelListener { finish(my, "Dibatalkan.", ERR) }.show()
        } else fill(my, plans, 0, 0, 0, skipped)
    }

    private fun buildPlans(qs: JSONArray, ans: JSONArray): List<Plan> {
        val byId = HashMap<String, JSONObject>()
        for (i in 0 until ans.length()) ans.optJSONObject(i)?.let { byId[it.optString("id")] = it }
        val out = ArrayList<Plan>()
        for (i in 0 until qs.length()) {
            val q = qs.getJSONObject(i); val id = q.getString("id")
            val a = byId[id] ?: continue
            if (a.optBoolean("skip", false)) continue
            val type = q.getString("type"); val opts = q.getJSONArray("options"); val title = q.getString("title")
            if (type == "text") {
                if (a.isNull("text")) continue
                val t = a.optString("text", "").trim()
                if (t.isNotEmpty() && t.length <= 500) out.add(Plan(id, type, emptyList(), t, title, t))
                continue
            }
            val ch = a.optJSONArray("choices") ?: continue
            val idx = (0 until ch.length()).map { ch.optInt(it, -1) }.filter { it in 0 until opts.length() }.distinct()
            if (idx.isEmpty()) continue
            if ((type == "single" || type == "select") && idx.size != 1) continue
            out.add(Plan(id, type, idx, null, title, idx.joinToString(", ") { opts.getString(it) }))
        }
        return out
    }

    private fun fill(my: Int, plans: List<Plan>, i: Int, ok: Int, bad: Int, skipped: Int) {
        if (my != runId) return
        if (stopped) { finish(my, "Dihentikan. $ok terisi sebelum berhenti.", ERR); return }
        if (i >= plans.size) {
            finish(my, "Selesai: $ok terisi, $bad gagal, $skipped dilewati. Periksa dulu, lalu tekan Lanjut/Kirim sendiri.", OK)
            return
        }
        setState("Mengisi ${i + 1}/${plans.size}…", BUSY)
        val p = plans[i]
        val arg = JSONObject().put("choices", JSONArray(p.choices)).put("text", p.text ?: JSONObject.NULL)
        web.evaluateJavascript("__orbit.fill('${p.id}',$arg)", null)
        ui.postDelayed({
            web.evaluateJavascript("__orbit.check('${p.id}')") { raw ->
                val good = verify(p, unwrap(raw))
                ui.postDelayed({ fill(my, plans, i + 1, ok + (if (good) 1 else 0), bad + (if (good) 0 else 1), skipped) }, 250)
            }
        }, 450)
    }

    private fun verify(p: Plan, raw: String?): Boolean = try {
        val o = JSONObject(raw ?: "")
        if (p.kind == "text") o.optString("text") == p.text
        else {
            val ch = o.getJSONArray("choices")
            (0 until ch.length()).map { ch.getInt(it) }.toSet().containsAll(p.choices)
        }
    } catch (e: Exception) { false }

    // ---------- pengaturan ----------
    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val keyEt = EditText(this).apply {
            hint = if (store.hasKey()) "API key tersimpan. Kosongkan untuk tetap." else "API key Gemini"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine()
        }
        val modelEt = EditText(this).apply { setText(store.model); hint = "Nama model Gemini"; setSingleLine() }
        val profEt = EditText(this).apply {
            setText(store.profile); minLines = 3
            hint = "Tentang saya (dikirim ke Gemini sebagai konteks jawaban)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val prevCb = CheckBox(this).apply { text = "Tampilkan jawaban dulu sebelum mengisi"; isChecked = store.preview }
        listOf(keyEt, modelEt, profEt, prevCb).forEach { box.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) }

        fun save() {
            val k = keyEt.text.toString().trim()
            if (k.isNotEmpty()) store.saveApiKey(k)
            store.model = modelEt.text.toString()
            store.profile = profEt.text.toString()
            store.preview = prevCb.isChecked
        }
        AlertDialog.Builder(this).setTitle("Pengaturan").setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Simpan") { _, _ -> save() }
            .setNeutralButton("Simpan & tes") { _, _ -> save(); testConnection() }
            .setNegativeButton("Batal", null).show()
    }

    private fun testConnection() {
        val key = store.apiKey() ?: run { toast("Isi API key dulu."); return }
        val model = store.model
        toast("Menguji koneksi…")
        io.execute {
            val r = GeminiClient.test(key, model)
            runOnUiThread { toast(r.fold({ "Koneksi OK. Model: $model" }, { it.message ?: "Gagal terhubung." })) }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }

    override fun onDestroy() { io.shutdownNow(); web.destroy(); super.onDestroy() }
}
