package id.franzz.orbit

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject

/** Satu kolom/pertanyaan aksesibilitas yang dapat disarankan jawabannya. */
class Field(val id: String, val title: String, val type: String) {
    val options = ArrayList<String>()
    val nodes = ArrayList<AccessibilityNodeInfo>()
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title.take(300)).put("type", type)
        .put("options", JSONArray(options.map { it.take(200) }))
}
class Screen(val pkg: String, val text: String, val fields: List<Field>)

/** Membaca accessibility tree aplikasi aktif; tidak mengambil screenshot dan mengabaikan password field. */
object ScreenReader {
    private const val MAX_NODES = 4000
    private const val MAX_TEXT = 30000

    fun targetRoot(svc: AccessibilityService): AccessibilityNodeInfo? {
        val own = svc.packageName
        var best: AccessibilityNodeInfo? = null
        var layer = -1
        try {
            for (window in svc.windows) {
                if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = window.root ?: continue
                if (root.packageName?.toString() == own) continue
                if (window.layer > layer) { best = root; layer = window.layer }
            }
        } catch (_: Exception) { }
        if (best != null) return best
        val active = try { svc.rootInActiveWindow } catch (_: Exception) { null }
        return if (active != null && active.packageName?.toString() != own) active else null
    }

    fun read(svc: AccessibilityService): Screen? {
        val root = targetRoot(svc) ?: return null
        val lines = ArrayList<String>()
        val fields = ArrayList<Field>()
        var label = ""
        var group: Field? = null
        var count = 0
        fun addLine(s: String) { if (s.isNotBlank() && (lines.isEmpty() || lines.last() != s)) lines.add(s) }
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (count++ > MAX_NODES || depth > 80) return
            if (!(try { node.isVisibleToUser } catch (_: Exception) { false })) return
            if (try { node.isPassword } catch (_: Exception) { false }) return
            val cls = node.className?.toString() ?: ""
            val text = (node.text?.toString() ?: "").trim()
            val shown = if (text.isNotEmpty()) text else if (node.childCount == 0) (node.contentDescription?.toString() ?: "").trim() else ""
            if (try { node.isEditable } catch (_: Exception) { false } || cls.endsWith("EditText")) {
                group = null
                val hint = try { node.hintText?.toString()?.trim().orEmpty() } catch (_: Exception) { "" }
                val title = hint.ifBlank { label }
                val f = Field("f${fields.size}", title.ifBlank { "Kolom ${fields.size + 1}" }, "text")
                f.nodes.add(node)
                fields.add(f)
            } else if (cls.endsWith("RadioButton") || cls.endsWith("CheckBox")) {
                val multi = cls.endsWith("CheckBox")
                val type = if (multi) "multi" else "single"
                var current = group
                if (current == null || current.type != type) {
                    current = Field("f${fields.size}", label.ifBlank { "Pertanyaan ${fields.size + 1}" }, type)
                    fields.add(current)
                    group = current
                }
                if (current.nodes.size < 30) {
                    current.nodes.add(node)
                    current.options.add(shown.ifBlank { "Pilihan ${current.nodes.size}" })
                }
            } else if (shown.isNotEmpty()) {
                addLine(shown.take(1000))
                label = shown.take(300)
                group = null
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return Screen(root.packageName?.toString() ?: "", lines.joinToString("\n").take(MAX_TEXT), fields)
    }

    fun apply(fields: List<Field>, answers: JSONArray): Int {
        var filled = 0
        for (i in 0 until answers.length()) {
            val answer = answers.optJSONObject(i) ?: continue
            val field = fields.firstOrNull { it.id == answer.optString("id") } ?: continue
            if (field.type == "text") {
                val value = answer.optString("text", "").trim()
                if (value.isNotEmpty() && field.nodes.isNotEmpty() && setText(field.nodes[0], value)) filled++
            } else {
                val chosen = answer.optJSONArray("choices") ?: continue
                val selected = (0 until chosen.length()).map { chosen.optInt(it, -1) }
                    .filter { it in field.nodes.indices }.toSet()
                var done = false
                if (field.type == "multi") {
                    // Synchronize every checkbox, not only the AI-selected ones, so old selections do not remain checked.
                    field.nodes.forEachIndexed { index, node ->
                        val checked = try { node.refresh(); node.isChecked } catch (_: Exception) { false }
                        val shouldBeChecked = index in selected
                        if (checked == shouldBeChecked) {
                            if (shouldBeChecked) done = true
                        } else if (click(node, false)) {
                            done = true
                        }
                    }
                } else {
                    val index = selected.firstOrNull()
                    if (index != null && field.nodes.getOrNull(index) != null) {
                        done = click(field.nodes[index], true)
                    }
                }
                if (done) filled++
            }
        }
        return filled
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        try { node.refresh() } catch (_: Exception) { return false }
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.take(500)) }
        return try { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) } catch (_: Exception) { false }
    }
    private fun click(node: AccessibilityNodeInfo, skipIfChecked: Boolean): Boolean {
        try { node.refresh() } catch (_: Exception) { return false }
        if (skipIfChecked && node.isChecked) return true
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops < 4) { target = target.parent; hops++ }
        return try { (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK) } catch (_: Exception) { false }
    }
}
