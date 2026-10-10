package id.franzz.orbit

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

/** Satu kolom/pertanyaan di layar: teks, pilihan tunggal (radio), atau pilihan ganda (checkbox). */
class Field(val id: String, val title: String, val type: String) {
    val options = ArrayList<String>()
    val nodes = ArrayList<AccessibilityNodeInfo>()
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title.take(300)).put("type", type)
        .put("options", JSONArray(options.map { it.take(200) }))
}

class Screen(val pkg: String, val text: String, val fields: List<Field>)

/** Membaca isi layar aplikasi/web yang sedang terbuka (bukan overlay bot sendiri) lewat layanan aksesibilitas. */
object ScreenReader {
    private const val MAX_NODES = 4000
    private const val MAX_TEXT = 30000

    /** Jendela aplikasi paling atas selain bot kita. Memakai daftar jendela agar tetap jalan saat panel bot sedang fokus. */
    fun targetRoot(svc: AccessibilityService): AccessibilityNodeInfo? {
        val own = svc.packageName
        var best: AccessibilityNodeInfo? = null
        var layer = -1
        for (w in svc.windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = w.root ?: continue
            if (r.packageName?.toString() == own) continue
            if (w.layer > layer) { best = r; layer = w.layer }
        }
        if (best != null) return best
        val a = svc.rootInActiveWindow
        return if (a != null && a.packageName?.toString() != own) a else null
    }

    fun read(svc: AccessibilityService): Screen? {
        val root = targetRoot(svc) ?: return null
        val lines = ArrayList<String>()
        val fields = ArrayList<Field>()
        var label = ""
        var group: Field? = null
        var count = 0

        fun addLine(s: String) { if (lines.isEmpty() || lines[lines.size - 1] != s) lines.add(s) }

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (count++ > MAX_NODES || depth > 80 || !n.isVisibleToUser) return
            if (n.isPassword) return // kolom password tidak pernah dibaca
            val cls = n.className?.toString() ?: ""
            val text = (n.text?.toString() ?: "").trim()
            val shown = if (text.isNotEmpty()) text else if (n.childCount == 0) (n.contentDescription?.toString() ?: "").trim() else ""
            if (n.isEditable || cls.endsWith("EditText")) {
                group = null
                val hint = (n.hintText?.toString() ?: "").trim()
                val title = if (hint.isNotEmpty()) hint else label
                val f = Field("f${fields.size}", if (title.isNotEmpty()) title else "Kolom ${fields.size + 1}", "text")
                f.nodes.add(n)
                fields.add(f)
            } else if (cls.endsWith("RadioButton") || cls.endsWith("CheckBox")) {
                val multi = cls.endsWith("CheckBox")
                val type = if (multi) "multi" else "single"
                var g = group
                if (g == null || g.type != type) {
                    g = Field("f${fields.size}", if (label.isNotEmpty()) label else "Pertanyaan ${fields.size + 1}", type)
                    fields.add(g)
                    group = g
                }
                if (g.nodes.size < 30) {
                    g.nodes.add(n)
                    g.options.add(if (shown.isNotEmpty()) shown else "Pilihan ${g.nodes.size}")
                }
            } else if (shown.isNotEmpty()) {
                addLine(shown)
                label = shown
                group = null
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c, depth + 1)
            }
        }
        walk(root, 0)
        return Screen(root.packageName?.toString() ?: "", lines.joinToString("\n").take(MAX_TEXT), fields)
    }

    /** Terapkan jawaban (id, text | choices) ke kolom di layar. Mengembalikan jumlah kolom yang terisi. */
    fun apply(fields: List<Field>, answers: JSONArray): Int {
        var filled = 0
        for (i in 0 until answers.length()) {
            val a = answers.optJSONObject(i) ?: continue
            val f = fields.firstOrNull { it.id == a.optString("id") } ?: continue
            if (f.type == "text") {
                val t = a.optString("text", "").trim()
                if (t.isNotEmpty() && f.nodes.isNotEmpty() && setText(f.nodes[0], t)) filled++
            } else {
                val ch = a.optJSONArray("choices") ?: continue
                var done = false
                for (k in 0 until ch.length()) {
                    val idx = ch.optInt(k, -1)
                    if (idx !in f.nodes.indices) continue
                    if (f.type == "single" && done) break
                    if (click(f.nodes[idx], f.type == "multi")) done = true
                }
                if (done) filled++
            }
        }
        return filled
    }

    private fun setText(node: AccessibilityNodeInfo, t: String): Boolean {
        node.refresh()
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t.take(500))
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun click(node: AccessibilityNodeInfo, skipIfChecked: Boolean): Boolean {
        node.refresh()
        if (skipIfChecked && node.isChecked) return true
        var x: AccessibilityNodeInfo? = node
        var hops = 0
        while (x != null && !x.isClickable && hops < 4) { x = x.parent; hops++ }
        return (x ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
