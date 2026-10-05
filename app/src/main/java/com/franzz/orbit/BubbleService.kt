package com.franzz.orbit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.ServiceCompat

/** Robot melayang di atas semua aplikasi. Ketuk robot = buka panel Scan & Fill untuk layar yang sedang terbuka. */
class BubbleService : Service() {
    companion object {
        const val SHOW = "com.franzz.orbit.SHOW"
        const val HIDE = "com.franzz.orbit.HIDE"
        private const val CH = "orbit_bubble"
        private const val INK = 0xFF1B1640.toInt()
    }

    private var wm: WindowManager? = null
    private var bubble: View? = null
    private var sprite: RobotSprite? = null
    private var panel: LinearLayout? = null
    private var runner: ExternalRunner? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var plp: WindowManager.LayoutParams
    private lateinit var store: SecureStore
    private lateinit var statusTv: TextView
    private lateinit var goBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var opLabel: TextView
    private val panelBg = GradientDrawable()
    private val fade = ArrayList<View>()
    private var curOp = 100
    private var panelOpen = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) addViews()
        val hide = intent?.action == HIDE
        bubble?.visibility = if (hide) View.GONE else View.VISIBLE
        if (hide) { setPanel(false); sprite?.stop() } else sprite?.start()
        applyOp(store.opacity)
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Robot melayang", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_robot)
            .setContentTitle("FRANZZ Orbit aktif")
            .setContentText("Ketuk robot di layar untuk Scan & Fill")
            .setContentIntent(pi).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    // ---------- tampilan ----------
    private fun mkBtn(label: String, bg: Int?): Button = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.WHITE); minHeight = 0; minimumHeight = 0
        background = if (bg == null) {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7B3FE4.toInt(), 0xFF2F6BFF.toInt())).apply { cornerRadius = dp(10).toFloat() }
        } else {
            GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(bg) }
        }
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun buildPanel(): LinearLayout {
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        panelBg.cornerRadius = dp(16).toFloat(); panelBg.setStroke(dp(2), INK); p.background = panelBg
        statusTv = TextView(this).apply {
            text = "Siap. Buka halaman survei, lalu tekan Scan & Fill."
            setTextColor(INK); typeface = Typeface.DEFAULT_BOLD; textSize = 13f
        }
        goBtn = mkBtn("Scan & Fill layar ini", null)
        stopBtn = mkBtn("STOP", 0xFFD9480F.toInt()).apply { visibility = View.GONE }
        val openBtn = mkBtn("Buka FRANZZ (link yang disalin)", INK)
        val dbgBtn = mkBtn("Salin struktur layar (debug)", 0xFF5A5780.toInt())
        val offBtn = mkBtn("Matikan robot", 0xFF5A5780.toInt())
        opLabel = TextView(this).apply { setTextColor(INK); textSize = 13f; setPadding(0, dp(6), 0, 0) }
        val seek = SeekBar(this).apply { max = 100; progress = store.opacity }
        val opRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, dp(8), dp(4))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.WHITE) }
        }
        opRow.addView(opLabel); opRow.addView(seek)
        for (v in listOf<View>(statusTv, goBtn, stopBtn, openBtn, dbgBtn, opRow, offBtn)) {
            p.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        }
        fade.addAll(listOf(goBtn, openBtn, dbgBtn, offBtn))

        goBtn.setOnClickListener {
            // gulir di sisi layar yang tidak tertutup panel
            runner?.swipeX = if (plp.x + dp(130) < resources.displayMetrics.widthPixels / 2) 0.8f else 0.2f
            runner?.start()
        }
        stopBtn.setOnClickListener { runner?.stop() }
        openBtn.setOnClickListener { setPanel(false); openApp() }
        dbgBtn.setOnClickListener {
            val svc = FillAccessibilityService.instance
            if (svc == null) {
                statusTv.text = "Layanan Aksesibilitas belum aktif."
            } else {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("struktur layar", svc.dump()))
                statusTv.text = "Struktur layar disalin. Isinya teks layar, tempel hanya ke orang yang kamu percaya."
            }
            placePanel()
        }
        offBtn.setOnClickListener { store.floating = false; stopSelf() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { applyOp(pr) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { store.opacity = seek.progress }
        })
        return p
    }

    private fun addViews() {
        store = SecureStore(this)
        val d = resources.displayMetrics
        val size = dp(56)
        val v = FrameLayout(this).apply { contentDescription = "FRANZZ Orbit" }
        val ring = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7B3FE4.toInt(), 0xFF2F6BFF.toInt())).apply {
                shape = GradientDrawable.OVAL; setStroke(dp(2), INK)
            }
        }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(2), dp(2), dp(2), dp(2)) }
        v.addView(ring, FrameLayout.LayoutParams(size, size))
        v.addView(icon, FrameLayout.LayoutParams(size, size))
        sprite = RobotSprite(icon)

        lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (store.obx < 0) d.widthPixels - size - dp(12) else store.obx
            y = if (store.oby < 0) d.heightPixels / 2 else store.oby
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var drag = false
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; drag = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (!drag && Math.hypot(dx.toDouble(), dy.toDouble()) > slop) drag = true
                    if (drag) {
                        lp.x = (ox + dx).toInt().coerceIn(0, maxOf(0, d.widthPixels - size))
                        lp.y = (oy + dy).toInt().coerceIn(0, maxOf(0, d.heightPixels - size))
                        wm?.updateViewLayout(v, lp)
                        placePanel()
                        sprite?.look(2 + Math.round((dx / dp(40)).coerceIn(-1f, 1f) * 2))
                    }
                }
                MotionEvent.ACTION_UP -> {
                    sprite?.look(2)
                    if (drag) { store.obx = lp.x; store.oby = lp.y } else setPanel(!panelOpen)
                }
            }
            true
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm?.addView(v, lp)
        bubble = v

        panel = buildPanel()
        plp = WindowManager.LayoutParams(dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        wm?.addView(panel, plp)

        runner = ExternalRunner(store,
            { msg, color ->
                statusTv.text = msg
                statusTv.setTextColor(if (color == ExternalRunner.ERR) color else INK)
                placePanel()
            },
            { running ->
                goBtn.visibility = if (running) View.GONE else View.VISIBLE
                stopBtn.visibility = if (running) View.VISIBLE else View.GONE
                placePanel()
            })
    }

    // 0% = transparan penuh. Saat panel terbuka robot tetap samar (30%), status dan STOP minimal 60%.
    private fun applyOp(p: Int) {
        curOp = p
        val a = p / 100f
        bubble?.alpha = if (panelOpen) maxOf(a, 0.3f) else a
        fade.forEach { it.alpha = a }
        statusTv.alpha = maxOf(a, 0.6f)
        stopBtn.alpha = maxOf(a, 0.6f)
        panelBg.setColor(Color.argb((maxOf(a, 0.12f) * 255).toInt(), 255, 255, 255))
        opLabel.text = "Opasitas $p%"
    }

    private fun setPanel(open: Boolean) {
        panelOpen = open
        panel?.visibility = if (open) View.VISIBLE else View.GONE
        if (open) placePanel()
        applyOp(curOp)
    }

    private fun placePanel() {
        val p = panel ?: return
        val dm = resources.displayMetrics
        val w = dp(260)
        p.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val h = p.measuredHeight
        val s = dp(56)
        plp.x = (lp.x + s - w).coerceIn(dp(8), maxOf(dp(8), dm.widthPixels - w - dp(8)))
        var y = lp.y - h - dp(8)
        if (y < dp(24)) y = lp.y + s + dp(8)
        plp.y = y.coerceIn(0, maxOf(0, dm.heightPixels - h))
        try { wm?.updateViewLayout(p, plp) } catch (e: Exception) { /* view belum atau sudah dilepas */ }
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("from_bubble", true))
    }

    override fun onDestroy() {
        runner?.shutdown()
        sprite?.stop()
        try { bubble?.let { wm?.removeView(it) } } catch (e: Exception) {}
        try { panel?.let { wm?.removeView(it) } } catch (e: Exception) {}
        bubble = null; panel = null
        super.onDestroy()
    }
}
