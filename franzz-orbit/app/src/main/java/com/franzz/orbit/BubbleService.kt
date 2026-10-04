package com.franzz.orbit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.ServiceCompat

/** Robot melayang di atas aplikasi lain. Hanya jalan pintas: ketuk = buka FRANZZ Orbit dengan link yang disalin. */
class BubbleService : Service() {
    companion object {
        const val SHOW = "com.franzz.orbit.SHOW"
        const val HIDE = "com.franzz.orbit.HIDE"
        private const val CH = "orbit_bubble"
    }

    private var wm: WindowManager? = null
    private var bubble: View? = null
    private var sprite: RobotSprite? = null
    private lateinit var lp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        if (bubble == null) addBubble()
        bubble?.visibility = if (intent?.action == HIDE) View.GONE else View.VISIBLE
        bubble?.alpha = SecureStore(this).opacity / 100f
        if (intent?.action == HIDE) sprite?.stop() else sprite?.start()
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Robot melayang", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CH).setSmallIcon(R.drawable.ic_robot)
            .setContentTitle("FRANZZ Orbit aktif")
            .setContentText("Salin link form, lalu ketuk robot")
            .setContentIntent(pi).setOngoing(true).build()
        ServiceCompat.startForeground(this, 1, n, if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    private fun addBubble() {
        val store = SecureStore(this)
        val d = resources.displayMetrics
        val size = (56 * d.density).toInt()
        val pad = (10 * d.density).toInt()
        val v = FrameLayout(this).apply { alpha = store.opacity / 100f; contentDescription = "FRANZZ Orbit" }
        val ring = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7B3FE4.toInt(), 0xFF2F6BFF.toInt())).apply {
                shape = GradientDrawable.OVAL; setStroke((2 * d.density).toInt(), 0xFF1B1640.toInt())
            }
        }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding((2 * d.density).toInt(), (2 * d.density).toInt(), (2 * d.density).toInt(), (2 * d.density).toInt()) }
        v.addView(ring, FrameLayout.LayoutParams(size, size))
        v.addView(icon, FrameLayout.LayoutParams(size, size))
        sprite = RobotSprite(icon)
        lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (store.obx < 0) d.widthPixels - size - (12 * d.density).toInt() else store.obx
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
                        sprite?.look(2 + Math.round((dx / (40 * d.density)).coerceIn(-1f, 1f) * 2))
                    }
                }
                MotionEvent.ACTION_UP -> { sprite?.look(2); if (drag) { store.obx = lp.x; store.oby = lp.y } else openApp() }
            }
            true
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm?.addView(v, lp)
        bubble = v
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("from_bubble", true))
    }

    override fun onDestroy() {
        sprite?.stop()
        bubble?.let { wm?.removeView(it) }
        bubble = null
        super.onDestroy()
    }
}
