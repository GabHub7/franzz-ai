package id.franzz.orbit

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val status: Int, message: String) : Exception(message)

/** Klien HTTP kecil untuk kontrak endpoint web FRANZZ Orbit. Semua request dibuat di background thread. */
object Api {
    fun normalizeServer(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return ""
        if (!value.startsWith("https://", true) && !value.startsWith("http://", true)) value = "https://$value"
        val uri = Uri.parse(value)
        if (!uri.scheme.equals("https", true)) throw ApiError(0, "Alamat server wajib memakai HTTPS.")
        if (uri.host.isNullOrBlank() || uri.userInfo != null || !uri.query.isNullOrEmpty() || !uri.fragment.isNullOrEmpty()) {
            throw ApiError(0, "Alamat server tidak valid. Isi domain saja, misalnya https://www.franzzorbit.my.id")
        }
        if (!uri.path.isNullOrEmpty() && uri.path != "/") {
            throw ApiError(0, "Alamat server harus berupa domain utama, tanpa path tambahan.")
        }
        return "https://${uri.host}${if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""}"
    }

    fun get(c: Context, path: String, server: String = Store.server(c)): JSONObject = request(c, "GET", path, null, false, server)

    fun call(c: Context, path: String, body: JSONObject?, auth: Boolean = true, server: String = Store.server(c)): JSONObject =
        request(c, "POST", path, body ?: JSONObject(), auth, server)

    private fun request(c: Context, method: String, path: String, body: JSONObject?, auth: Boolean, server: String): JSONObject {
        val base = normalizeServer(server)
        if (base.isEmpty()) throw ApiError(0, "Isi alamat server dulu.")
        if (!path.startsWith("/api/")) throw ApiError(0, "Endpoint tidak valid.")
        val conn = (URL(base + path).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Cache-Control", "no-store")
            if (method == "POST") {
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
            }
            if (auth) {
                val token = Store.token(c) ?: throw ApiError(401, "Belum masuk. Buka aplikasi FRANZZ Orbit lalu masuk.")
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null && method == "POST") {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try { if (text.isBlank()) JSONObject() else JSONObject(text) } catch (_: Exception) { null }
            if (code !in 200..299) {
                if (code == 401 && auth) Store.clearToken(c)
                val msg = json?.optString("error", "")?.takeIf { it.isNotBlank() } ?: "Server menjawab dengan kode $code."
                throw ApiError(code, msg)
            }
            return json ?: throw ApiError(code, "Respons server bukan JSON yang valid.")
        } catch (e: ApiError) {
            throw e
        } catch (e: IOException) {
            throw e
        } finally {
            conn.disconnect()
        }
    }

    fun describe(e: Exception): String = when (e) {
        is ApiError -> when {
            e.status == 401 -> "Sesi habis atau belum masuk. Buka aplikasi FRANZZ Orbit dan masuk lagi."
            e.message.isNullOrBlank() -> "Permintaan gagal."
            else -> e.message ?: "Permintaan gagal."
        }
        is IOException -> "Tidak bisa menghubungi server. Periksa koneksi internet dan alamat server."
        else -> e.message ?: "Terjadi kesalahan yang tidak diketahui."
    }
}
