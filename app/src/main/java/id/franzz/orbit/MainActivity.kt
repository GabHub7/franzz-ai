package id.franzz.orbit

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val ui = Handler(Looper.getMainLooper())
    private var profileEt: EditText? = null
    private val ink = 0xFF201B49.toInt()
    private val muted = 0xFF706A8E.toInt()
    private val accent = 0xFF6B50E5.toInt()
    private val red = 0xFFB3261E.toInt()
    private val green = 0xFF18794E.toInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFFF5F3FF.toInt()
        window.navigationBarColor = 0xFFF5F3FF.toInt()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (intent.getBooleanExtra("requestAudioPermission", false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7402)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFFF5F3FF.toInt())
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onPause() {
        saveProfile()
        super.onPause()
    }

    private fun saveProfile() { profileEt?.let { Store.setProfile(this, it.text.toString()) } }

    private fun label(value: String, size: Float, bold: Boolean = false, color: Int = ink): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(3), 0, dp(3))
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun edit(hintText: String, input: Int, minLines: Int = 1): EditText = EditText(this).apply {
        hint = hintText
        inputType = input
        setTextColor(ink)
        setHintTextColor(0xFF9A94B5.toInt())
        textSize = 15f
        this.minLines = minLines
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = rounded(0xFFFFFFFF.toInt(), 0xFFDCD6F3.toInt(), 12)
        if (minLines > 1) gravity = Gravity.TOP
    }

    private fun rounded(fill: Int, stroke: Int = 0, radius: Int = 14): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun button(title: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        setTextColor(if (primary) Color.WHITE else ink)
        background = rounded(if (primary) accent else Color.WHITE, if (primary) accent else 0xFFDCD6F3.toInt(), 12)
        minHeight = dp(46)
        minimumHeight = dp(46)
        setPadding(dp(14), dp(7), dp(14), dp(7))
        setOnClickListener { action() }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Color.WHITE, 0xFFE1DCF4.toInt(), 18)
    }

    private fun gap(height: Int) { root.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(height))) }

    private fun render() {
        profileEt = null
        root.removeAllViews()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val robot = ImageView(this).apply {
            try { setImageBitmap(assets.open("robot.png").use { BitmapFactory.decodeStream(it) }) } catch (_: Exception) { }
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Robot FRANZZ"
        }
        header.addView(robot, LinearLayout.LayoutParams(dp(58), dp(58)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        heading.addView(label("FRANZZ Orbit", 24f, true))
        heading.addView(label("Asisten AI untuk belajar dan membantu di layar", 12.5f, false, muted))
        header.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)
        gap(18)
        if (Store.token(this) == null) loginView() else mainView()
    }

    private fun loginView() {
        val card = card()
        card.addView(label("Masuk ke akun", 19f, true))
        card.addView(label("Gunakan akun FRANZZ Orbit yang sama seperti di website.", 13f, false, muted))
        gap(12)
        val server = edit("Alamat server HTTPS", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        server.setText(Store.server(this))
        val user = edit("Username", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        user.setText(Store.username(this))
        val pass = edit("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val msg = label("", 13f, false, red)
        listOf(server, user, pass).forEach { field ->
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(10)
            card.addView(field, lp)
        }
        card.addView(msg)
        lateinit var login: Button
        login = button("Masuk", true) {
            val base: String
            try { base = Api.normalizeServer(server.text.toString()) }
            catch (e: Exception) { msg.text = Api.describe(e); return@button }
            val username = user.text.toString().trim()
            val password = pass.text.toString()
            if (username.isEmpty() || password.isEmpty()) { msg.text = "Isi username dan password."; return@button }
            msg.text = "Menghubungkan ke server…"
            val btn = login
            btn.isEnabled = false
            Thread {
                try {
                    val response = Api.call(this, "/api/login", JSONObject().put("username", username).put("password", password), false, base)
                    val token = response.optString("token", "")
                    if (token.isBlank()) throw ApiError(0, "Respons login tidak valid.")
                    Store.login(this, base, token, response.optString("username", username))
                    ui.post { OrbitAccessibilityService.instance?.sync(); render() }
                } catch (e: Exception) {
                    ui.post { msg.text = Api.describe(e); btn.isEnabled = true }
                }
            }.start()
        }
        card.addView(login, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        card.addView(button("Cek koneksi server", false) { checkServer(server.text.toString(), msg) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        root.addView(card)
        gap(12)
        root.addView(label("Catatan: kunci Gemini dan kredensial Firebase tetap tersimpan di server. APK hanya mengirim permintaan melalui HTTPS.", 12.5f, false, muted))
    }

    private fun checkServer(raw: String, msg: TextView) {
        val base = try { Api.normalizeServer(raw) } catch (e: Exception) { msg.text = Api.describe(e); return }
        msg.setTextColor(muted)
        msg.text = "Memeriksa server dan status Firestore…"
        Thread {
            try {
                val status = Api.get(this, "/api/status", base)
                val configured = status.optBoolean("configured", false)
                val panelReady = status.optBoolean("panel", false)
                val adminReady = status.optBoolean("adminReady", false)
                val storageError = status.optString("storageError", "")
                val message = when {
                    !configured -> status.optString("message", "Konfigurasi server belum lengkap.")
                    storageError.isNotBlank() -> "Server merespons, tetapi Firestore bermasalah: $storageError"
                    !panelReady -> "Server online, tetapi Firestore belum siap. Periksa FIREBASE_SERVICE_ACCOUNT_JSON dan izin IAM di Vercel."
                    !adminReady -> "Firestore siap, tetapi ENV ADMIN_USERNAME / ADMIN_PASSWORD belum terdeteksi oleh server."
                    else -> "Server dan Firestore siap. Login dan fitur AI bisa diuji."
                }
                ui.post {
                    msg.setTextColor(if (configured && panelReady && storageError.isBlank()) green else red)
                    msg.text = message
                }
            } catch (e: Exception) { ui.post { msg.setTextColor(red); msg.text = Api.describe(e) } }
        }.start()
    }

    private fun serviceOn(): Boolean {
        if (OrbitAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val component = ComponentName(this, OrbitAccessibilityService::class.java)
        return enabled.split(':').any { it.equals(component.flattenToString(), true) || it.equals(component.flattenToShortString(), true) }
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "Aktifkan layanan Bot FRANZZ di daftar Aksesibilitas.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun mainView() {
        val account = card()
        account.addView(label("Akun terhubung", 18f, true))
        account.addView(label("Masuk sebagai ${Store.username(this)}", 13f, false, muted))
        val status = label("", 13f, true)
        fun refresh() {
            val on = serviceOn()
            status.text = if (on) "Layanan aksesibilitas aktif" else "Layanan aksesibilitas belum aktif"
            status.setTextColor(if (on) green else red)
        }
        val sw = Switch(this).apply {
            text = "Bot FRANZZ aktif"
            textSize = 16f
            setTextColor(ink)
            isChecked = Store.botOn(this@MainActivity)
        }
        sw.setOnCheckedChangeListener { _, on ->
            Store.setBotOn(this, on)
            if (on && !serviceOn()) openAccessibilitySettings()
            refresh()
        }
        account.addView(sw)
        account.addView(status)
        account.addView(button("Buka pengaturan Aksesibilitas", false) { openAccessibilitySettings() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        account.addView(button("Cek koneksi server", false) {
            val msg = label("Memeriksa…", 13f, false, muted)
            account.addView(msg)
            checkServer(Store.server(this), msg)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        refresh()
        root.addView(account)
        gap(12)

        val help = card()
        help.addView(label("Cara pakai", 18f, true))
        help.addView(label(
            "1. Aktifkan layanan Bot FRANZZ di Aksesibilitas. Jika Android memblokirnya, buka Info aplikasi lalu izinkan pengaturan terbatas.\n\n" +
            "2. Nyalakan Bot FRANZZ aktif. Ketuk robot melayang untuk membuka menu.\n\n" +
            "3. Di menu yang sama tersedia Scan & Fill, Analisis Halaman AI, Fitur AI, dan Chat AI.\n\n" +
            "Teks layar dibaca melalui layanan Aksesibilitas, bukan screenshot. Password field diabaikan. Chat hanya menyertakan isi layar jika opsi tersebut dinyalakan; Scan & Fill dan Analisis AI membaca layar saat kamu menjalankannya. Periksa jawaban sebelum diterapkan.",
            13f, false, muted))
        root.addView(help)
        gap(12)

        val profileCard = card()
        profileCard.addView(label("Profil untuk Scan & Fill", 18f, true))
        profileCard.addView(label("Isi hanya info yang ingin kamu gunakan saat menjawab formulir. Jangan masukkan password atau data sangat sensitif.", 12.5f, false, muted))
        val profile = edit("Contoh: kelas XI RPL, minat pemrograman…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, 3)
        profile.setText(Store.profile(this))
        profileEt = profile
        profileCard.addView(profile, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        profileCard.addView(button("Simpan profil", true) {
            Store.setProfile(this, profile.text.toString())
            Toast.makeText(this, "Profil disimpan di perangkat.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        root.addView(profileCard)
        gap(12)
        root.addView(button("Keluar", false) {
            saveProfile()
            Store.logout(this)
            OrbitAccessibilityService.instance?.sync()
            render()
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7402) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(this, if (granted) "Izin mikrofon aktif." else "Izin mikrofon ditolak; fitur suara tidak tersedia.", Toast.LENGTH_LONG).show()
        }
    }
}
