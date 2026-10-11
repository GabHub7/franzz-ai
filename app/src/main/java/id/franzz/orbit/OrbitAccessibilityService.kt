package id.franzz.orbit

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent

class OrbitAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: OrbitAccessibilityService? = null }
    private var overlay: BotOverlay? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> sync() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = BotOverlay(this)
        Store.sp(this).registerOnSharedPreferenceChangeListener(listener)
        sync()
    }
    fun sync() {
        val current = overlay ?: return
        if (Store.botOn(this) && Store.token(this) != null) current.show() else current.hide()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
    private fun cleanup() {
        try { Store.sp(this).unregisterOnSharedPreferenceChangeListener(listener) } catch (_: Exception) { }
        overlay?.dispose()
        overlay = null
        if (instance === this) instance = null
    }
}
