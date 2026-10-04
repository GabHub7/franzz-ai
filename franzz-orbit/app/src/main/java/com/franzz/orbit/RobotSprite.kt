package com.franzz.orbit

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat

/** Menganimasikan robot 3D (gambar pra-render): melayang naik-turun, menoleh saat digeser, dan berkedip. */
class RobotSprite(private val view: ImageView) {
    private val ctx = view.context
    private val frames: List<Drawable> = listOf(
        R.drawable.robot_yn30, R.drawable.robot_yn15, R.drawable.robot_y0, R.drawable.robot_yp15, R.drawable.robot_yp30
    ).map { ContextCompat.getDrawable(ctx, it)!! }
    private val blinkFrame: Drawable = ContextCompat.getDrawable(ctx, R.drawable.robot_blink)!!
    private val h = Handler(Looper.getMainLooper())
    private var bob: ObjectAnimator? = null
    private var cur = 2
    private val blink = object : Runnable {
        override fun run() {
            view.setImageDrawable(blinkFrame)
            h.postDelayed({ look(cur) }, 130)
            h.postDelayed(this, 3000L + (Math.random() * 3000).toLong())
        }
    }

    init { look(2) }

    /** 0 = menoleh kiri jauh, 2 = lurus, 4 = menoleh kanan jauh. */
    fun look(i: Int) { cur = i.coerceIn(0, 4); view.setImageDrawable(frames[cur]) }

    fun start() {
        stop(); look(2)
        // Hormati pengaturan "hapus animasi" milik sistem.
        if (Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return
        val amp = 3f * ctx.resources.displayMetrics.density
        bob = ObjectAnimator.ofFloat(view, "translationY", -amp, amp).apply {
            duration = 1400; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }
        h.postDelayed(blink, 2500)
    }

    fun stop() { bob?.cancel(); bob = null; view.translationY = 0f; h.removeCallbacksAndMessages(null) }
}
