package com.franzz.orbit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XQuestion(
    val id: String,
    val title: String,
    val type: String,               // single | multi | text
    val options: List<String>,
    val nodes: List<AccessibilityNodeInfo>
) {
    /** Tanda tangan soal, dipakai untuk mencocokkan ulang setelah memindai lagi. */
    val sig: String get() = (title + "|" + options.joinToString(",")).hashCode().toString()
}

class XScan(val questions: List<XQuestion>, val blocked: String?)

/**
 * Hanya bekerja saat pengguna mengetuk Scan & Fill. Tidak membaca layar yang berisi kolom password,
 * tidak membaca layar sistem, dan tidak pernah menekan tombol (Kirim/Lanjut/dll).
 */
class FillAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: FillAccessibilityService? = null
        private val NAV = Regex(
            "(submit|send|kirim|selesai|finish|done|konfirmasi|confirm|complete|simpan|save|next|berikutnya|selanjutnya|lanjut|lanjutkan|continue|back|kembali|clear|hapus|kosongkan)",
            RegexOption.IGNORE_CASE
        )
        private val BLOCKED_PKGS = setOf(
            "com.android.settings", "com.android.systemui", "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.packageinstaller"
        )
    }


    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---------- pindai ----------
    fun scan(): XScan? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString().orEmpty()
        if (pkg == packageName) return XScan(emptyList(), "Buka halaman survei di browser dulu. Ini layar FRANZZ Orbit sendiri.")
        if (pkg in BLOCKED_PKGS) return XScan(emptyList(), "Layar sistem tidak dipindai.")
        val items = ArrayList<GItem<AccessibilityNodeInfo>>()
        collect(root, items, 0)
        if (items.any { it.kind == GK.PASSWORD }) return XScan(emptyList(), "Layar berisi kolom password. Tidak dipindai.")
        return XScan(Grouper.group(items).mapIndexed { i, g -> XQuestion("q${i + 1}", g.title, g.type, g.options, g.nodes) }, null)
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<GItem<AccessibilityNodeInfo>>, depth: Int) {
        if (n == null || depth > 60 || out.size > 800 || !n.isVisibleToUser) return
        val cls = n.className?.toString().orEmpty()
        val label = (n.text ?: n.contentDescription)?.toString()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val kind: GK? = when {
            n.isPassword -> GK.PASSWORD
            cls.endsWith("RadioButton") -> GK.RADIO
            cls.endsWith("CheckBox") -> GK.CHECK
            n.isEditable || cls.endsWith("EditText") -> GK.EDIT
            n.isClickable && cls.endsWith("Button") -> GK.BUTTON
            else -> null
        }
        if (kind != null) {
            out.add(GItem(kind, if (kind == GK.EDIT) n.hintText?.toString().orEmpty() else label, n, n.isEnabled))
            return
        }
        if (label.isNotEmpty() && out.lastOrNull()?.label != label) out.add(GItem(GK.TEXT, label, n))
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1)
    }

    // ---------- isi & verifikasi ----------
    fun fill(q: XQuestion, choices: List<Int>, text: String?): Boolean {
        if (q.type == "text") {
            val n = q.nodes.firstOrNull() ?: return false
            if (n.isPassword || text.isNullOrEmpty()) return false
            n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        var ok = true
        for (i in choices) {
            val n = q.nodes.getOrNull(i)
            if (n == null) { ok = false; continue }
            n.refresh()
            if (!n.isChecked && !click(n)) ok = false
        }
        return ok
    }

    // Hanya mengklik pilihan jawaban. Induk yang dikliknya tidak boleh berupa tombol atau bertuliskan Kirim/Lanjut/dll.
    private fun click(n: AccessibilityNodeInfo): Boolean {
        if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var p = n.parent
        var d = 0
        while (p != null && d < 3) {
            val cls = p.className?.toString().orEmpty()
            val l = (p.text ?: p.contentDescription)?.toString().orEmpty()
            if (p.isClickable && !cls.endsWith("Button") && !NAV.containsMatchIn(l)) {
                return p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            p = p.parent; d++
        }
        return false
    }

    fun verify(q: XQuestion, choices: List<Int>, text: String?): Boolean {
        if (q.type == "text") {
            val n = q.nodes.firstOrNull() ?: return false
            n.refresh()
            return n.text?.toString() == text
        }
        return choices.all { i ->
            val n = q.nodes.getOrNull(i)
            if (n == null) false else { n.refresh(); n.isChecked }
        }
    }

    // ---------- gulir ----------
    fun swipeUp(xFrac: Float, done: (Boolean) -> Unit) {
        val dm = resources.displayMetrics
        val path = Path().apply {
            moveTo(dm.widthPixels * xFrac, dm.heightPixels * 0.72f)
            lineTo(dm.widthPixels * xFrac, dm.heightPixels * 0.34f)
        }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build()
        val started = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { done(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { done(false) }
        }, null)
        if (!started) done(false)
    }

    // ---------- debug: struktur layar untuk dianalisis (berisi teks layar, dibagikan atas kemauan pengguna) ----------
    fun dump(): String {
        val root = rootInActiveWindow ?: return "(tidak ada jendela aktif)"
        val sb = StringBuilder("pkg=").append(root.packageName).append('\n')
        fun cut(s: CharSequence?): String = s?.toString()?.replace("\n", " ")?.take(60).orEmpty()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > 40 || sb.length > 60000) return
            sb.append("  ".repeat(d)).append(n.className?.toString()?.substringAfterLast('.') ?: "?")
                .append(if (n.isVisibleToUser) " V" else " -")
                .append(if (n.isClickable) "C" else "").append(if (n.isCheckable) "K" else "")
                .append(if (n.isChecked) "X" else "").append(if (n.isEditable) "E" else "")
                .append(" t=").append(cut(n.text)).append(" d=").append(cut(n.contentDescription)).append('\n')
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return sb.toString()
    }
}
