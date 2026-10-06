package com.franzz.orbit

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Alur untuk survei di aplikasi/browser lain: pindai layar, tanya Gemini, cocokkan ulang node, isi, verifikasi,
 * lalu gulir dan ulangi sampai dasar halaman. Tidak pernah menekan Lanjut/Kirim.
 */
class ExternalRunner(
    private val store: SecureStore,
    private val onStatus: (String, Int) -> Unit,
    private val onRunning: (Boolean) -> Unit
) {
    companion object {
        const val BUSY = 0xFFE0A100.toInt()
        const val OK = 0xFF1F9D63.toInt()
        const val ERR = 0xFFD9480F.toInt()
    }

    private class XPlan(val q: XQuestion, val choices: List<Int>, val text: String?)

    private val h = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var token = 0
    private val handled = HashSet<String>()
    private var filled = 0
    private var failed = 0
    private var skipped = 0
    private var passes = 0
    private var idle = 0
    var swipeX = 0.2f
    var running = false
        private set

    fun start() {
        if (running) return
        if (FillAccessibilityService.instance == null) {
            onStatus("Layanan Aksesibilitas belum aktif. Buka FRANZZ Orbit lalu nyalakan Robot melayang.", ERR); return
        }
        if (!store.loggedIn()) { onStatus("Belum masuk. Buka FRANZZ Orbit lalu login dulu.", ERR); return }
        running = true; token++
        handled.clear(); filled = 0; failed = 0; skipped = 0; passes = 0; idle = 0
        onRunning(true)
        pass(token)
    }

    fun stop() {
        if (!running) return
        val t = ++token
        end(t, "Dihentikan. $filled terisi.", ERR)
    }

    fun shutdown() { token++; running = false; io.shutdownNow() }

    private fun end(my: Int, msg: String, color: Int) {
        if (my != token) return
        running = false
        onRunning(false)
        onStatus(msg, color)
    }

    private fun summary(prefix: String) =
        "$prefix $filled terisi, $failed gagal, $skipped dilewati. Periksa dulu, lalu tekan Lanjut/Kirim sendiri."

    private fun pass(my: Int) {
        if (my != token) return
        val svc = FillAccessibilityService.instance
        if (svc == null) { end(my, "Layanan Aksesibilitas mati. Nyalakan lagi.", ERR); return }
        if (++passes > 25) { end(my, summary("Berhenti: batas 25 layar."), OK); return }
        onStatus("Memindai layar ($passes)…", BUSY)
        val sc = svc.scan()
        if (sc == null) { end(my, "Tidak bisa membaca layar. Coba lagi.", ERR); return }
        val blocked = sc.blocked
        if (blocked != null) { end(my, blocked, ERR); return }

        val fresh = sc.questions.filter { it.sig !in handled }
        if (fresh.isEmpty()) {
            if (++idle >= 2) end(my, summary("Selesai."), OK) else scroll(my)
            return
        }
        idle = 0
        val token = store.token()
        if (token == null) { end(my, "Belum masuk. Buka FRANZZ Orbit lalu login dulu.", ERR); return }
        onStatus("Menganalisis ${fresh.size} soal…", BUSY)
        val arr = JSONArray()
        fresh.forEach { q ->
            arr.put(JSONObject().put("id", q.id).put("title", q.title).put("type", q.type)
                .put("options", JSONArray(q.options)).put("required", false))
        }
        val server = store.serverUrl
        val profile = store.profile
        io.execute {
            val res = GeminiClient.answer(server, token, profile, arr)
            h.post { onAnswers(my, fresh, res) }
        }
    }

    private fun onAnswers(my: Int, fresh: List<XQuestion>, res: Result<JSONArray>) {
        if (my != token) return
        val ans = res.getOrElse {
            if (it is AuthException) store.logout()
            end(my, it.message ?: "Gagal memanggil Gemini.", ERR); return
        }
        val plans = buildPlans(fresh, ans)
        fresh.forEach { handled.add(it.sig) }
        skipped += fresh.size - plans.size
        if (plans.isEmpty()) { scroll(my); return }
        val svc = FillAccessibilityService.instance
        if (svc == null) { end(my, "Layanan Aksesibilitas mati. Nyalakan lagi.", ERR); return }
        // Pindai ulang dan cocokkan lewat tanda tangan soal, agar node yang dipakai masih segar.
        val now = svc.scan()?.questions?.associateBy { it.sig } ?: emptyMap()
        fill(my, plans, 0, now)
    }

    private fun fill(my: Int, plans: List<XPlan>, i: Int, now: Map<String, XQuestion>) {
        if (my != token) return
        if (i >= plans.size) { scroll(my); return }
        val svc = FillAccessibilityService.instance
        if (svc == null) { end(my, "Layanan Aksesibilitas mati. Nyalakan lagi.", ERR); return }
        val p = plans[i]
        onStatus("Mengisi… (${filled + failed + 1})", BUSY)
        val q = now[p.q.sig]
        if (q != null) svc.fill(q, p.choices, p.text)
        h.postDelayed({
            if (my == token) {
                val good = q != null && svc.verify(q, p.choices, p.text)
                if (good) filled++ else failed++
                fill(my, plans, i + 1, now)
            }
        }, 150)
    }

    private fun scroll(my: Int) {
        if (my != token) return
        val svc = FillAccessibilityService.instance
        if (svc == null) { end(my, "Layanan Aksesibilitas mati. Nyalakan lagi.", ERR); return }
        onStatus("Menggulir…", BUSY)
        svc.swipeUp(swipeX) { h.postDelayed({ pass(my) }, 800) }
    }

    private fun buildPlans(qs: List<XQuestion>, ans: JSONArray): List<XPlan> {
        val byId = HashMap<String, JSONObject>()
        for (i in 0 until ans.length()) ans.optJSONObject(i)?.let { byId[it.optString("id")] = it }
        val out = ArrayList<XPlan>()
        for (q in qs) {
            val a = byId[q.id] ?: continue
            if (a.optBoolean("skip", false)) continue
            if (q.type == "text") {
                if (a.isNull("text")) continue
                val t = a.optString("text", "").trim()
                if (t.isNotEmpty() && t.length <= 500) out.add(XPlan(q, emptyList(), t))
                continue
            }
            val ch = a.optJSONArray("choices") ?: continue
            val idx = (0 until ch.length()).map { ch.optInt(it, -1) }.filter { it in 0 until q.options.size }.distinct()
            if (idx.isEmpty()) continue
            if (q.type == "single" && idx.size != 1) continue
            out.add(XPlan(q, idx, null))
        }
        return out
    }
}
