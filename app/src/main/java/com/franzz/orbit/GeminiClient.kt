package com.franzz.orbit

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sesi tidak valid atau akun dinonaktifkan: pengguna harus masuk lagi. */
class AuthException(message: String) : Exception(message)

/**
 * Semua panggilan AI lewat server FRANZZ Orbit (API key Gemini dipegang admin di server).
 * Yang dikirim hanya teks soal, pilihan, dan profil pengguna.
 */
object GeminiClient {
    private fun base(server: String): String {
        val s = server.trim().trimEnd('/')
        require(s.startsWith("https://") && s.length > 10) { "Alamat server harus diawali https://" }
        return s
    }

    private fun req(url: String, token: String?, body: JSONObject): Pair<Int, JSONObject> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 70000; doOutput = true
            setRequestProperty("content-type", "application/json")
            if (token != null) setRequestProperty("authorization", "Bearer $token")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
        return code to json
    }

    fun login(server: String, user: String, pass: String): Result<JSONObject> = runCatching {
        val (code, j) = req(base(server) + "/api/login", null, JSONObject().put("username", user).put("password", pass))
        if (code != 200) error(j.optString("error", "Gagal masuk ($code)."))
        j
    }

    fun answer(server: String, token: String, profile: String, questions: JSONArray): Result<JSONArray> = runCatching {
        val (code, j) = req(base(server) + "/api/gemini", token, JSONObject().put("profile", profile).put("questions", questions))
        when {
            code == 200 -> j.getJSONArray("answers")
            code == 401 || code == 403 -> throw AuthException(j.optString("error", "Sesi berakhir. Masuk lagi."))
            else -> error(j.optString("error", "Server error ($code)."))
        }
    }
}
