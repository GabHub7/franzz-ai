package id.franzz.orbit

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val status: Int, message: String) : Exception(message)

/** Klien HTTP sederhana ke backend Orbit (tanpa library). Panggil dari thread latar belakang. */
object Api {
    fun normalizeServer(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    fun call(c: Context, path: String, body: JSONObject?, auth: Boolean = true, server: String = Store.server(c)): JSONObject {
        val base = normalizeServer(server)
        if (!base.startsWith("https://")) throw ApiError(0, "Alamat server harus diawali https://")
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            if (auth) {
                val t = Store.token(c) ?: throw ApiError(401, "Belum masuk. Buka aplikasi Franzz lalu masuk.")
                conn.setRequestProperty("Authorization", "Bearer $t")
            }
            conn.doOutput = true
            conn.outputStream.use { it.write((body ?: JSONObject()).toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try { JSONObject(text) } catch (e: Exception) { null }
            if (code !in 200..299) {
                if (code == 401 && auth) Store.clearToken(c)
                val msg = json?.optString("error", "")?.takeIf { it.isNotEmpty() } ?: "Server menjawab dengan kode $code."
                throw ApiError(code, msg)
            }
            return json ?: JSONObject()
        } finally {
            conn.disconnect()
        }
    }

    fun describe(e: Exception): String = when (e) {
        is ApiError -> if (e.status == 401) "Sesi habis. Buka aplikasi Franzz lalu masuk lagi." else e.message ?: "Gagal."
        is java.io.IOException -> "Tidak bisa menghubungi server. Cek koneksi."
        else -> e.message ?: "Terjadi kesalahan."
    }
}
