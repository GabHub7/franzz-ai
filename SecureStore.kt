package com.franzz.orbit

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Token sesi dienkripsi AES-GCM dengan kunci di Android Keystore. Token tidak pernah di-log. */
class SecureStore(ctx: Context) {
    companion object { private const val ALIAS = "franzz_orbit_key" }

    private val prefs = ctx.getSharedPreferences("franzz_orbit", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return g.generateKey()
    }

    private fun putSecret(name: String, v: String?) {
        if (v == null) { prefs.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(v.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    private fun secret(name: String): String? = try {
        val raw = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw, 12, raw.size - 12))
    } catch (e: Exception) { null }

    // ---- sesi login (akun dibuat oleh admin) ----
    var serverUrl: String
        get() = prefs.getString("server", "") ?: ""
        set(v) = prefs.edit().putString("server", v.trim().trimEnd('/')).apply()
    var username: String
        get() = prefs.getString("user", "") ?: ""
        set(v) = prefs.edit().putString("user", v).apply()
    fun token(): String? = secret("tok")
    fun saveSession(server: String, user: String, token: String) { serverUrl = server; username = user; putSecret("tok", token) }
    fun loggedIn() = serverUrl.isNotEmpty() && token() != null
    fun logout() { putSecret("tok", null) }

    // ---- preferensi ----
    var profile: String
        get() = prefs.getString("profile", "") ?: ""
        set(v) = prefs.edit().putString("profile", v.trim()).apply()
    var preview: Boolean
        get() = prefs.getBoolean("preview", false)
        set(v) = prefs.edit().putBoolean("preview", v).apply()
    var opacity: Int
        get() = prefs.getInt("opacity", 100)
        set(v) = prefs.edit().putInt("opacity", v.coerceIn(0, 100)).apply()
    var bx: Float
        get() = prefs.getFloat("bx", -1f)
        set(v) = prefs.edit().putFloat("bx", v).apply()
    var by: Float
        get() = prefs.getFloat("by", -1f)
        set(v) = prefs.edit().putFloat("by", v).apply()
    var floating: Boolean
        get() = prefs.getBoolean("floating", false)
        set(v) = prefs.edit().putBoolean("floating", v).apply()
    var obx: Int
        get() = prefs.getInt("obx", -1)
        set(v) = prefs.edit().putInt("obx", v).apply()
    var oby: Int
        get() = prefs.getInt("oby", -1)
        set(v) = prefs.edit().putInt("oby", v).apply()
    var autoNext: Boolean
        get() = prefs.getBoolean("autoNext", false)
        set(v) = prefs.edit().putBoolean("autoNext", v).apply()
}
