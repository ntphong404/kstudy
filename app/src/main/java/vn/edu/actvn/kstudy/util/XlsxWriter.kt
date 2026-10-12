package vn.edu.actvn.kstudy.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// The order of this enum is the order of the <xf> entries written to xl/styles.xml
enum class XlsxStyle {
    DEFAULT,
    TITLE,
    HEADER,
    BODY,
    BODY_CENTER,
    PRESENT,
    LATE,
    ABSENT,
    WARNING,
    DANGER
}

data class XlsxCell(
    val text: String = "",
    val number: Double? = null,
    val style: XlsxStyle = XlsxStyle.DEFAULT
) {
    companion object {
        fun text(text: String, style: XlsxStyle = XlsxStyle.BODY) = XlsxCell(text = text, style = style)
        fun number(value: Int, style: XlsxStyle = XlsxStyle.BODY_CENTER) =
            XlsxCell(number = value.toDouble(), style = style)
    }
}

data class XlsxSheet(
    val name: String,
    val rows: List<List<XlsxCell>>,
    val columnWidths: List<Int> = emptyList() // in characters, one per column; missing columns keep the default width
)

// Writes a minimal .xlsx workbook (a zip of XML parts) without any third-party library,
// which keeps the APK small: Apache POI alone would add several MB.
object XlsxWriter {

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"

    fun write(sheets: List<XlsxSheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "A workbook needs at least one sheet" }
        val sheetNames = uniqueSheetNames(sheets.map { it.name })
        val sharedStrings = LinkedHashMap<String, Int>()
        val sheetParts = sheets.map { sheetXml(it, sharedStrings) }

        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypesXml(sheets.size))
            zip.put("_rels/.rels", rootRelsXml())
            zip.put("xl/workbook.xml", workbookXml(sheetNames))
            zip.put("xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            zip.put("xl/styles.xml", stylesXml())
            zip.put("xl/sharedStrings.xml", sharedStringsXml(sharedStrings.keys))
            sheetParts.forEachIndexed { index, xml ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", xml)
            }
        }
    }

    // 0 -> "A", 25 -> "Z", 26 -> "AA"
    fun columnName(index: Int): String {
        require(index >= 0) { "Column index must not be negative" }
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.append('A' + n % 26)
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.reverse().toString()
    }

    // Excel rejects sheet names that are empty, longer than 31 characters or contain \ / ? * [ ] :
    fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().trim('\'').take(31).trim()
        return cleaned.ifEmpty { "Sheet" }
    }

    fun escapeXml(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                // Control characters are not allowed in XML 1.0 and would make the file unreadable
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                ch == '￾' || ch == '￿' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun uniqueSheetNames(names: List<String>): List<String> {
        val used = HashSet<String>()
        return names.map { raw ->
            val base = sanitizeSheetName(raw)
            var candidate = base
            var suffix = 2
            while (!used.add(candidate.lowercase())) {
                val tail = " ($suffix)"
                candidate = base.take(31 - tail.length) + tail
                suffix++
            }
            candidate
        }
    }

    private fun ZipOutputStream.put(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sheetXml(sheet: XlsxSheet, sharedStrings: MutableMap<String, Int>): String {
        val sb = StringBuilder(XML_HEADER)
        sb.append("<worksheet xmlns=\"$NS_MAIN\">")
        if (sheet.columnWidths.isNotEmpty()) {
            sb.append("<cols>")
            sheet.columnWidths.forEachIndexed { index, width ->
                val col = index + 1
                sb.append("<col min=\"$col\" max=\"$col\" width=\"$width\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            sb.append("<row r=\"$rowNumber\">")
            row.forEachIndexed { colIndex, cell ->
                val ref = columnName(colIndex) + rowNumber
                val styleId = cell.style.ordinal
                when {
                    cell.number != null ->
                        sb.append("<c r=\"$ref\" s=\"$styleId\"><v>${formatNumber(cell.number)}</v></c>")
                    cell.text.isEmpty() ->
                        sb.append("<c r=\"$ref\" s=\"$styleId\"/>")
                    else -> {
                        val stringId = sharedStrings.getOrPut(cell.text) { sharedStrings.size }
                        sb.append("<c r=\"$ref\" s=\"$styleId\" t=\"s\"><v>$stringId</v></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    // Whole numbers are written without a fraction so Excel shows "5", not "5.0"
    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()

    private fun sharedStringsXml(strings: Collection<String>): String {
        val sb = StringBuilder(XML_HEADER)
        sb.append("<sst xmlns=\"$NS_MAIN\" uniqueCount=\"${strings.size}\">")
        for (s in strings) {
            sb.append("<si><t xml:space=\"preserve\">").append(escapeXml(s)).append("</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun contentTypesXml(sheetCount: Int): String {
        val sb = StringBuilder(XML_HEADER)
        sb.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        sb.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        sb.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        sb.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (i in 1..sheetCount) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        sb.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        sb.append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
        sb.append("</Types>")
        return sb.toString()
    }

    private fun rootRelsXml(): String =
        XML_HEADER +
            "<Relationships xmlns=\"$NS_PKG_REL\">" +
            "<Relationship Id=\"rId1\" Type=\"$NS_REL/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private fun workbookXml(sheetNames: List<String>): String {
        val sb = StringBuilder(XML_HEADER)
        sb.append("<workbook xmlns=\"$NS_MAIN\" xmlns:r=\"$NS_REL\"><sheets>")
        sheetNames.forEachIndexed { index, name ->
            val id = index + 1
            sb.append("<sheet name=\"${escapeXml(name)}\" sheetId=\"$id\" r:id=\"rId$id\"/>")
        }
        sb.append("</sheets></workbook>")
        return sb.toString()
    }

    private fun workbookRelsXml(sheetCount: Int): String {
        val sb = StringBuilder(XML_HEADER)
        sb.append("<Relationships xmlns=\"$NS_PKG_REL\">")
        for (i in 1..sheetCount) {
            sb.append("<Relationship Id=\"rId$i\" Type=\"$NS_REL/worksheet\" Target=\"worksheets/sheet$i.xml\"/>")
        }
        sb.append("<Relationship Id=\"rId${sheetCount + 1}\" Type=\"$NS_REL/styles\" Target=\"styles.xml\"/>")
        sb.append("<Relationship Id=\"rId${sheetCount + 2}\" Type=\"$NS_REL/sharedStrings\" Target=\"sharedStrings.xml\"/>")
        sb.append("</Relationships>")
        return sb.toString()
    }

    // The <xf> entries of cellXfs must stay in the same order as XlsxStyle
    private fun stylesXml(): String {
        val border = "borderId=\"1\" applyBorder=\"1\""
        val center = "<alignment horizontal=\"center\" vertical=\"center\"/>"
        val centerWrap = "<alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/>"
        val left = "<alignment vertical=\"center\"/>"
        return XML_HEADER +
            "<styleSheet xmlns=\"$NS_MAIN\">" +
            "<fonts count=\"5\">" +
            "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FFB71C1C\"/><name val=\"Calibri\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FF8D6E00\"/><name val=\"Calibri\"/></font>" +
            "</fonts>" +
            // Fills 0 and 1 are reserved by the file format
            "<fills count=\"6\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            solidFill("FF5B21B6") +
            solidFill("FFE8F5E9") +
            solidFill("FFFFF3C4") +
            solidFill("FFFFEBEE") +
            "</fills>" +
            "<borders count=\"2\">" +
            "<border><left/><right/><top/><bottom/><diagonal/></border>" +
            "<border>" +
            "<left style=\"thin\"><color rgb=\"FFC3C6D6\"/></left>" +
            "<right style=\"thin\"><color rgb=\"FFC3C6D6\"/></right>" +
            "<top style=\"thin\"><color rgb=\"FFC3C6D6\"/></top>" +
            "<bottom style=\"thin\"><color rgb=\"FFC3C6D6\"/></bottom>" +
            "<diagonal/></border>" +
            "</borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"${XlsxStyle.values().size}\">" +
            // DEFAULT
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            // TITLE
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
            // HEADER
            "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" $border xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">$centerWrap</xf>" +
            // BODY
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" $border xfId=\"0\" applyAlignment=\"1\">$left</xf>" +
            // BODY_CENTER
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" $border xfId=\"0\" applyAlignment=\"1\">$center</xf>" +
            // PRESENT
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" $border xfId=\"0\" applyFill=\"1\" applyAlignment=\"1\">$center</xf>" +
            // LATE
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"4\" $border xfId=\"0\" applyFill=\"1\" applyAlignment=\"1\">$center</xf>" +
            // ABSENT
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"5\" $border xfId=\"0\" applyFill=\"1\" applyAlignment=\"1\">$center</xf>" +
            // WARNING
            "<xf numFmtId=\"0\" fontId=\"4\" fillId=\"4\" $border xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">$center</xf>" +
            // DANGER
            "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"5\" $border xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\">$center</xf>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"
    }

    private fun solidFill(argb: String): String =
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"$argb\"/><bgColor indexed=\"64\"/></patternFill></fill>"
}
