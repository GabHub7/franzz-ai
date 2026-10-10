package id.franzz.orbit

import android.content.Context
import android.content.SharedPreferences

/** Penyimpanan lokal (privat milik aplikasi): alamat server, token sesi, profil, status bot. */
object Store {
    fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("orbit", Context.MODE_PRIVATE)

    fun server(c: Context): String = sp(c).getString("server", null)?.takeIf { it.isNotEmpty() } ?: BuildConfig.DEFAULT_SERVER
    fun token(c: Context): String? = sp(c).getString("token", null)?.takeIf { it.isNotEmpty() }
    fun username(c: Context): String = sp(c).getString("username", "") ?: ""
    fun profile(c: Context): String = sp(c).getString("profile", "") ?: ""
    fun botOn(c: Context): Boolean = sp(c).getBoolean("botOn", false)

    fun login(c: Context, server: String, token: String, username: String) {
        sp(c).edit().putString("server", server).putString("token", token).putString("username", username).apply()
    }
    fun logout(c: Context) { sp(c).edit().remove("token").putBoolean("botOn", false).apply() }
    fun clearToken(c: Context) { sp(c).edit().remove("token").apply() }
    fun setBotOn(c: Context, on: Boolean) { sp(c).edit().putBoolean("botOn", on).apply() }
    fun setProfile(c: Context, p: String) { sp(c).edit().putString("profile", p.take(2000)).apply() }

    fun pos(c: Context): Pair<Int, Int> = Pair(sp(c).getInt("bx", 24), sp(c).getInt("by", 360))
    fun setPos(c: Context, x: Int, y: Int) { sp(c).edit().putInt("bx", x).putInt("by", y).apply() }
}
