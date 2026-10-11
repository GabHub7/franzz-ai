package id.franzz.orbit

import android.content.Context
import android.content.SharedPreferences

/** Preferensi lokal aplikasi. Token tidak pernah ditaruh di APK, URL, atau log. */
object Store {
    private const val FILE = "franzz_orbit_private"
    fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun server(c: Context): String = sp(c).getString("server", null)?.trim()?.takeIf { it.isNotEmpty() }
        ?: BuildConfig.DEFAULT_SERVER
    fun token(c: Context): String? = sp(c).getString("token", null)?.takeIf { it.isNotEmpty() }
    fun username(c: Context): String = sp(c).getString("username", "") ?: ""
    fun profile(c: Context): String = sp(c).getString("profile", "") ?: ""
    fun botOn(c: Context): Boolean = sp(c).getBoolean("botOn", false)
    fun login(c: Context, server: String, token: String, username: String) {
        sp(c).edit().putString("server", server).putString("token", token).putString("username", username).apply()
    }
    fun logout(c: Context) {
        sp(c).edit().remove("token").remove("username").putBoolean("botOn", false).apply()
    }
    fun clearToken(c: Context) {
        sp(c).edit().remove("token").remove("username").putBoolean("botOn", false).apply()
    }
    fun setBotOn(c: Context, on: Boolean) { sp(c).edit().putBoolean("botOn", on).apply() }
    fun setProfile(c: Context, profile: String) { sp(c).edit().putString("profile", profile.take(2000)).apply() }
    fun pos(c: Context): Pair<Int, Int> = Pair(sp(c).getInt("bx", 20), sp(c).getInt("by", 220))
    fun setPos(c: Context, x: Int, y: Int) { sp(c).edit().putInt("bx", x.coerceAtLeast(0)).putInt("by", y.coerceAtLeast(0)).apply() }
}
