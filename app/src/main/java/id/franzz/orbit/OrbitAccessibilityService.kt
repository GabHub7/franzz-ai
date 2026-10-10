package id.franzz.orbit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Layanan aksesibilitas: dipakai untuk (1) membaca layar aplikasi/web yang terbuka dan (2) menampilkan robot Franzz
 * sebagai overlay di atas aplikasi apa pun. Robot hanya tampil bila sudah masuk dan tombol "Bot Franzz aktif" menyala.
 */
class OrbitAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: OrbitAccessibilityService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private var overlay: BotOverlay? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> sync() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = BotOverlay(this)
        Store.sp(this).registerOnSharedPreferenceChangeListener(listener)
        sync()
    }

    /** Samakan tampilan robot dengan pengaturan terbaru (dipanggil saat toggle atau login berubah). */
    fun sync() {
        ui.post {
            val o = overlay ?: return@post
            if (Store.botOn(this) && Store.token(this) != null) o.show() else o.hide()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        Store.sp(this).unregisterOnSharedPreferenceChangeListener(listener)
        overlay?.hide()
        overlay = null
        if (instance === this) instance = null
    }
}
