package vn.edu.actvn.kstudy.util

// Reads the text of a CSV file into rows of cells. A cell may be wrapped in double quotes to
// hold the delimiter, a line break or a quote (written twice), as spreadsheet apps export it.
object CsvParser {

    private val DELIMITERS = charArrayOf(',', ';', '\t')

    // Every line of the file gives one row, an empty line included, so that the position of a
    // row in the result is its line number in the file
    fun parse(text: String): List<List<String>> {
        val content = text.removePrefix("﻿")
        if (content.isEmpty()) return emptyList()
        val delimiter = detectDelimiter(content)

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        fun endRow() {
            row.add(cell.toString())
            cell.setLength(0)
            rows.add(row)
            row = mutableListOf()
        }

        while (index < content.length) {
            val ch = content[index]
            when {
                quoted && ch == '"' && content.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                quoted && ch == '"' -> quoted = false
                quoted -> cell.append(ch)
                // A quote only opens a quoted cell at its very start; elsewhere it is plain text
                ch == '"' && cell.isEmpty() -> quoted = true
                ch == delimiter -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                }
                ch == '\r' -> {
                    if (content.getOrNull(index + 1) == '\n') index++
                    endRow()
                }
                ch == '\n' -> endRow()
                else -> cell.append(ch)
            }
            index++
        }
        // The last line usually ends with a line break, which must not add an empty row
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    // Excel separates with ";" where the comma is the decimal mark, as in Vietnam, and text
    // copied out of a spreadsheet uses tabs. The first line that has any of them decides.
    internal fun detectDelimiter(text: String): Char {
        for (line in text.lineSequence()) {
            var quoted = false
            val counts = IntArray(DELIMITERS.size)
            for (ch in line) {
                if (ch == '"') quoted = !quoted
                val which = DELIMITERS.indexOf(ch)
                if (!quoted && which >= 0) counts[which]++
            }
            val most = counts.indices.maxByOrNull { counts[it] } ?: 0
            if (counts[most] > 0) return DELIMITERS[most]
        }
        return ','
    }
}
