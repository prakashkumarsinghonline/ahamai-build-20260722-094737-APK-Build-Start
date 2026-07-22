package com.ahamai.app.data

/**
 * Computes line-level diffs between two text versions (LCS-based),
 * for showing green (+added) / red (-removed) changes with counts.
 */
object DiffUtil {

    data class DiffLine(val type: Char, val text: String, val oldNum: Int = 0, val newNum: Int = 0) // '+', '-', ' '
    data class DiffResult(
        val added: Int,
        val removed: Int,
        val lines: List<DiffLine>
    )

    fun compute(oldText: String, newText: String, maxPreview: Int = 60): DiffResult {
        val a = oldText.split("\n")
        val b = newText.split("\n")

        val m = a.size
        val n = b.size
        val lcs = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val result = mutableListOf<DiffLine>()
        var added = 0
        var removed = 0
        var i = 0
        var j = 0
        var oldNum = 1
        var newNum = 1
        while (i < m && j < n) {
            when {
                a[i] == b[j] -> { result.add(DiffLine(' ', a[i], oldNum, newNum)); i++; j++; oldNum++; newNum++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { result.add(DiffLine('-', a[i], oldNum, 0)); removed++; i++; oldNum++ }
                else -> { result.add(DiffLine('+', b[j], 0, newNum)); added++; j++; newNum++ }
            }
        }
        while (i < m) { result.add(DiffLine('-', a[i], oldNum, 0)); removed++; i++; oldNum++ }
        while (j < n) { result.add(DiffLine('+', b[j], 0, newNum)); added++; j++; newNum++ }

        // Compact preview: changed lines + 1 line context around them
        val preview = mutableListOf<DiffLine>()
        for ((idx, line) in result.withIndex()) {
            if (line.type != ' ') {
                preview.add(line)
            } else if ((idx > 0 && result[idx - 1].type != ' ') || (idx < result.size - 1 && result[idx + 1].type != ' ')) {
                preview.add(line)
            }
            if (preview.size >= maxPreview) break
        }

        return DiffResult(added, removed, preview)
    }
}
