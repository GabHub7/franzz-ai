package com.franzz.orbit

enum class GK { TEXT, RADIO, CHECK, EDIT, BUTTON, PASSWORD }
class GItem<N>(val kind: GK, val label: String, val node: N, val enabled: Boolean = true)
class GQ<N>(val title: String, val type: String, val options: List<String>, val nodes: List<N>)

/** Mengelompokkan daftar elemen layar (urutan baca) menjadi soal. Murni Kotlin agar bisa diuji tanpa Android. */
object Grouper {
    private val OTHER = Regex("^(other|lainnya)\\b", RegexOption.IGNORE_CASE)

    private fun pickTitle(recent: List<String>): String =
        recent.takeLast(3).filter { t -> t.length >= 4 }.maxByOrNull { t -> t.length } ?: ""

    fun <N> group(items: List<GItem<N>>): List<GQ<N>> {
        val out = ArrayList<GQ<N>>()
        val recent = ArrayList<String>()
        var i = 0
        while (i < items.size) {
            val cur = items[i]
            when (cur.kind) {
                GK.TEXT -> { recent.add(cur.label); i++ }
                GK.RADIO, GK.CHECK -> {
                    val k = cur.kind
                    val nodes = ArrayList<N>()
                    val labels = ArrayList<String>()
                    var pending = ""
                    // skala 1..5: angka sering berupa teks terpisah tepat sebelum tombol radio
                    if (cur.label.isEmpty() && recent.isNotEmpty() && recent.last().length <= 12) pending = recent.removeAt(recent.size - 1)
                    var j = i
                    while (j < items.size) {
                        val x = items[j]
                        if (x.kind == k) {
                            nodes.add(x.node); labels.add(if (x.label.isNotEmpty()) x.label else pending); pending = ""; j++
                        } else if (x.kind == GK.TEXT && x.label.length <= 12 && j + 1 < items.size &&
                            items[j + 1].kind == k && items[j + 1].label.isEmpty()) {
                            pending = x.label; j++
                        } else break
                    }
                    // Kelompok yang menyentuh tepi bawah layar dianggap belum lengkap: diproses setelah menggulir.
                    val complete = j < items.size
                    val title = pickTitle(recent)
                    val keep = labels.indices.filter { idx -> labels[idx].isNotEmpty() && !OTHER.containsMatchIn(labels[idx]) }
                    val need = if (k == GK.RADIO) 2 else 1
                    if (complete && title.isNotEmpty() && keep.size >= need) {
                        out.add(GQ(title, if (k == GK.RADIO) "single" else "multi", keep.map { idx -> labels[idx] }, keep.map { idx -> nodes[idx] }))
                    }
                    recent.clear(); i = j
                }
                GK.EDIT -> {
                    val title = pickTitle(recent)
                    if (title.isNotEmpty() && cur.enabled) out.add(GQ(title, "text", emptyList(), listOf(cur.node)))
                    recent.clear(); i++
                }
                else -> { i++ }
            }
        }
        return out
    }
}
