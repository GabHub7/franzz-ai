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

/** Layar utama: masuk ke server Orbit, tombol on/off Bot Franzz, status layanan aksesibilitas, dan profil untuk Isi kolom. */
class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var profileEt: EditText? = null
    private val ui = Handler(Looper.getMainLooper())

    private val ink = 0xFF1B1640.toInt()
    private val muted = 0xFF5A5780.toInt()
    private val err = 0xFFB3261E.toInt()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFFF1EFFC.toInt())
        sv.isFillViewport = true
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(32), dp(20), dp(32))
        sv.addView(root)
        setContentView(sv)
        if (intent.getBooleanExtra("requestAudioPermission", false) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7402)
        }
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

    // ---------------------------------------------------------------- bahan UI

    private fun text(s: String, size: Float, bold: Boolean, color: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        t.setPadding(0, dp(4), 0, dp(4))
        return t
    }

    private fun edit(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setTextColor(ink)
        return e
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(if (primary) Color.WHITE else ink)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(12).toFloat()
        bg.setColor(if (primary) 0xFF6A4FE0.toInt() else Color.WHITE)
        if (!primary) bg.setStroke(dp(1), 0xFFCFCBE6.toInt())
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    private fun gap(h: Int) { root.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(h))) }

    private fun serviceOn(): Boolean {
        if (OrbitAccessibilityService.instance != null) return true
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val cn = ComponentName(this, OrbitAccessibilityService::class.java)
        return flat.split(':').any { it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true) }
    }

    private fun openAccessibilitySettings() {
        Toast.makeText(this, "Nyalakan \"Bot Franzz\" di daftar layanan.", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ---------------------------------------------------------------- tampilan

    private fun render() {
        profileEt = null
        root.removeAllViews()
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val robot = ImageView(this)
        try { robot.setImageBitmap(assets.open("robot.png").use { BitmapFactory.decodeStream(it) }) } catch (e: Exception) { }
        head.addView(robot, LinearLayout.LayoutParams(dp(56), dp(56)))
        val ttl = text("Franzz Orbit", 24f, true, ink)
        ttl.setPadding(dp(12), 0, 0, 0)
        head.addView(ttl)
        root.addView(head)
        gap(12)
        if (Store.token(this) == null) loginView() else mainView()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7402) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(this, if (granted) "Izin mikrofon aktif. Kembali ke aplikasi yang ingin kamu gunakan." else "Izin mikrofon ditolak; fitur suara tidak bisa digunakan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loginView() {
        root.addView(text("Masuk dengan akun Orbit milikmu. Setelah itu robot Franzz bisa muncul di atas web atau aplikasi apa pun.", 14f, false, muted))
        gap(8)
        val server = edit("Alamat server (https://…)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        server.setText(Store.server(this))
        val user = edit("Username", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        user.setText(Store.username(this))
        val pass = edit("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val msg = text("", 13f, false, err)
        root.addView(server)
        root.addView(user)
        root.addView(pass)
        root.addView(msg)
        val btn = button("Masuk", true) {}
        btn.setOnClickListener {
            val srv = Api.normalizeServer(server.text.toString())
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (!srv.startsWith("https://")) { msg.text = "Alamat server harus diawali https://"; return@setOnClickListener }
            if (u.isEmpty() || p.isEmpty()) { msg.text = "Isi username dan password."; return@setOnClickListener }
            msg.text = ""
            btn.isEnabled = false
            Thread {
                try {
                    val r = Api.call(this, "/api/login", JSONObject().put("username", u).put("password", p), false, srv)
                    val token = r.optString("token", "")
                    if (token.isEmpty()) throw ApiError(0, "Respons login tidak valid.")
                    Store.login(this, srv, token, r.optString("username", u))
                    ui.post { render() }
                } catch (e: Exception) {
                    ui.post { msg.text = if (e is ApiError && e.message != null) e.message else Api.describe(e); btn.isEnabled = true }
                }
            }.start()
        }
        root.addView(btn)
    }

    private fun mainView() {
        root.addView(text("Masuk sebagai ${Store.username(this)}", 14f, false, muted))
        gap(8)
        val status = text("", 14f, true, ink)
        fun refresh() {
            val on = serviceOn()
            status.text = if (on) "Layanan aksesibilitas: AKTIF ✓" else "Layanan aksesibilitas: BELUM aktif"
            status.setTextColor(if (on) 0xFF1B7F3B.toInt() else err)
        }
        val sw = Switch(this)
        sw.text = "Bot Franzz aktif"
        sw.textSize = 18f
        sw.setTextColor(ink)
        sw.isChecked = Store.botOn(this)
        sw.setOnCheckedChangeListener { _, on ->
            Store.setBotOn(this, on)
            if (on && !serviceOn()) openAccessibilitySettings()
            refresh()
        }
        root.addView(sw)
        gap(4)
        root.addView(status)
        refresh()
        root.addView(button("Buka pengaturan aksesibilitas", false) { openAccessibilitySettings() })
        gap(8)
        root.addView(button("Aktifkan izin mikrofon", false) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin mikrofon sudah aktif.", Toast.LENGTH_SHORT).show()
            } else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7402)
        })
        gap(8)
        root.addView(text(
            "Cara pakai\n" +
            "1. Nyalakan layanan \"Bot Franzz\" di Aksesibilitas. Kalau Android menolak (\"Pengaturan terbatas\"): Info aplikasi Franzz Orbit, titik tiga di kanan atas, lalu Izinkan pengaturan terbatas.\n" +
            "2. Nyalakan tombol \"Bot Franzz aktif\" di atas. Robot muncul melayang.\n" +
            "3. Buka web atau aplikasi apa saja, ketuk robot, lalu tanya atau tekan \"Isi kolom di layar\".\n" +
            "Robot hanya membaca layar saat kamu mengirim pesan dengan opsi Sertakan layar, atau menekan Isi kolom. Kolom password tidak pernah dibaca. Tombol 🎙 bisa dipakai untuk bertanya dengan suara dan jawaban akan dibacakan. Izinkan mikrofon saat diminta.",
            13f, false, muted))
        gap(12)
        root.addView(text("Tentang saya (dipakai untuk \"Isi kolom\")", 14f, true, ink))
        val prof = edit("Contoh: Nama Budi, kelas XI RPL, hobi coding", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        prof.minLines = 3
        prof.gravity = Gravity.TOP
        prof.setText(Store.profile(this))
        profileEt = prof
        root.addView(prof)
        gap(16)
        root.addView(button("Keluar", false) {
            saveProfile()
            Store.logout(this)
            render()
        })
    }
}
