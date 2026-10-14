package vn.edu.actvn.kstudy.util

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

// Reads the first sheet of an .xlsx workbook (a zip of XML parts) into rows of text, without
// any third-party library, like XlsxWriter. Every cell comes back the way a person reads it in
// the spreadsheet: numbers without a needless fraction, dates as dd/MM/yyyy.
object XlsxReader {

    // A list of students is tiny; anything near these limits is not one
    private const val MAX_PART_BYTES = 20 * 1024 * 1024
    private const val MAX_ROWS = 5000
    private const val MAX_COLUMNS = 200

    private const val WORKBOOK = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"

    // The position of a row in the result is its row number in the spreadsheet: empty rows are kept
    fun read(input: InputStream): List<List<String>> {
        // java.util.zip reads the directory at the end of a zip only from a file. Going through
        // a stream entry by entry instead fails on workbooks whose writer left the sizes out of
        // the entry headers, as Apache POI does in streaming mode.
        val file = File.createTempFile("kstudy-import", ".xlsx")
        try {
            file.outputStream().use { input.copyTo(it) }
            return ZipFile(file).use { read(unzip(it)) }
        } finally {
            file.delete()
        }
    }

    private fun read(parts: Map<String, ByteArray>): List<List<String>> {
        val workbookXml = parts[WORKBOOK] ?: throw IOException("Not an .xlsx workbook: $WORKBOOK is missing")

        val relations = Relations()
        parts[WORKBOOK_RELS]?.let { parse(it, relations) }
        val workbook = Workbook()
        parse(workbookXml, workbook)

        val sheetPath = relations.targets[workbook.firstSheetRelationId]
            ?: relations.firstOfType("worksheet")
            ?: "xl/worksheets/sheet1.xml"
        val sheetXml = parts[sheetPath] ?: throw IOException("The first sheet is missing: $sheetPath")

        val sharedStrings = SharedStrings()
        parts[relations.firstOfType("sharedStrings") ?: "xl/sharedStrings.xml"]?.let { parse(it, sharedStrings) }
        val styles = Styles()
        parts[relations.firstOfType("styles") ?: "xl/styles.xml"]?.let { parse(it, styles) }

        val sheet = Sheet(sharedStrings.strings, styles.dateStyles(), workbook.epoch1904)
        parse(sheetXml, sheet)
        return sheet.rows
    }

    // 0-based column of a cell reference: "A1" -> 0, "AB12" -> 27
    internal fun columnIndex(cellReference: String): Int {
        var index = 0
        for (ch in cellReference) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    // Spreadsheets store a date as the number of days since 30/12/1899 (or 01/01/1904 in
    // workbooks made by old versions of Excel for Mac)
    internal fun serialToDate(serial: Double, epoch1904: Boolean = false): String {
        val day = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            clear()
            if (epoch1904) set(1904, Calendar.JANUARY, 1) else set(1899, Calendar.DECEMBER, 30)
            add(Calendar.DAY_OF_YEAR, Math.floor(serial).toInt())
        }
        return String.format(
            Locale.US, "%02d/%02d/%04d",
            day.get(Calendar.DAY_OF_MONTH), day.get(Calendar.MONTH) + 1, day.get(Calendar.YEAR)
        )
    }

    // True for the number formats that show a number as a date
    internal fun isDateFormat(numFmtId: Int, formatCode: String?): Boolean {
        if (formatCode == null) {
            // Built-in formats: 14-22 are dates and times, 27-36 and 50-58 are East Asian dates
            return numFmtId in 14..22 || numFmtId in 27..36 || numFmtId in 45..47 || numFmtId in 50..58
        }
        // What is left after dropping quoted text, [colours and conditions] and escaped characters
        val pattern = formatCode
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("\\\\."), "")
        return pattern.any { it in "dDmMyY" }
    }

    private fun unzip(zip: ZipFile): Map<String, ByteArray> {
        val parts = HashMap<String, ByteArray>()
        for (entry in zip.entries()) {
            val name = entry.name.trimStart('/')
            if (!entry.isDirectory && name.startsWith("xl/") && (name.endsWith(".xml") || name.endsWith(".rels"))) {
                parts[name] = zip.getInputStream(entry).use { readPart(it, name) }
            }
        }
        return parts
    }

    private fun readPart(input: InputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            out.write(buffer, 0, count)
            if (out.size() > MAX_PART_BYTES) throw IOException("$name is too large")
        }
        return out.toByteArray()
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        try {
            // A workbook has no reason to carry a DTD; refusing it rules out entity tricks.
            // Not every parser knows this switch (Android's does not), so a failure is ignored.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: Exception) {
            // The parser keeps its defaults
        }
        factory.newSAXParser().parse(InputSource(ByteArrayInputStream(xml)), handler)
    }

    // Element and attribute names without their namespace prefix, whichever way the parser reports them
    private fun plainName(localName: String?, qName: String?): String =
        (if (localName.isNullOrEmpty()) qName.orEmpty() else localName).substringAfter(':')

    private fun Attributes.local(name: String): String? {
        for (i in 0 until length) {
            if (plainName(getLocalName(i), getQName(i)) == name) return getValue(i)
        }
        return null
    }

    // xl/_rels/workbook.xml.rels: where the parts of the workbook are
    private class Relations : DefaultHandler() {
        val targets = HashMap<String, String>() // relationship id -> path inside the zip
        private val types = LinkedHashMap<String, String>() // path -> relationship type

        fun firstOfType(type: String): String? = types.entries.firstOrNull { it.value.endsWith("/$type") }?.key

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            if (plainName(localName, qName) != "Relationship") return
            val target = attributes.local("Target") ?: return
            // Targets are relative to the xl folder unless they start at the root of the zip
            val path = if (target.startsWith("/")) target.trimStart('/') else "xl/$target"
            attributes.local("Id")?.let { targets[it] = path }
            types[path] = attributes.local("Type").orEmpty()
        }
    }

    // xl/workbook.xml: the order of the sheets and the date system
    private class Workbook : DefaultHandler() {
        var firstSheetRelationId: String? = null
        var epoch1904 = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            when (plainName(localName, qName)) {
                "sheet" -> if (firstSheetRelationId == null) firstSheetRelationId = attributes.local("id")
                "workbookPr" -> epoch1904 = attributes.local("date1904") in setOf("1", "true")
            }
        }
    }

    // xl/sharedStrings.xml: the texts of the workbook, which cells refer to by position
    private class SharedStrings : DefaultHandler() {
        val strings = ArrayList<String>()
        private val item = StringBuilder()
        private var inText = false
        private var inPhonetic = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            when (plainName(localName, qName)) {
                "si" -> item.setLength(0)
                "t" -> inText = !inPhonetic
                // Reading aid of East Asian text, not part of what the cell shows
                "rPh" -> inPhonetic = true
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (plainName(localName, qName)) {
                "si" -> strings.add(item.toString())
                "t" -> inText = false
                "rPh" -> inPhonetic = false
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) item.append(ch, start, length)
        }
    }

    // xl/styles.xml: which cell styles show their number as a date
    private class Styles : DefaultHandler() {
        private val customFormats = HashMap<Int, String>()
        private val formatOfStyle = ArrayList<Int>()
        private var inCellXfs = false

        fun dateStyles(): List<Boolean> = formatOfStyle.map { isDateFormat(it, customFormats[it]) }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            when (plainName(localName, qName)) {
                "numFmt" -> {
                    val id = attributes.local("numFmtId")?.toIntOrNull() ?: return
                    customFormats[id] = attributes.local("formatCode").orEmpty()
                }
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) formatOfStyle.add(attributes.local("numFmtId")?.toIntOrNull() ?: 0)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (plainName(localName, qName) == "cellXfs") inCellXfs = false
        }
    }

    // xl/worksheets/sheetN.xml: the cells
    private class Sheet(
        private val sharedStrings: List<String>,
        private val dateStyles: List<Boolean>,
        private val epoch1904: Boolean
    ) : DefaultHandler() {
        val rows = ArrayList<List<String>>()

        private var row = ArrayList<String>()
        private var column = 0
        private var cellType = ""
        private var cellStyle = 0
        private val value = StringBuilder()
        private var inValue = false

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            when (plainName(localName, qName)) {
                "row" -> {
                    // Rows without any cell are not written to the file: the gap is filled in
                    val number = attributes.local("r")?.toIntOrNull() ?: (rows.size + 1)
                    while (rows.size < minOf(number - 1, MAX_ROWS)) rows.add(emptyList())
                    row = ArrayList()
                }
                "c" -> {
                    column = attributes.local("r")?.let { columnIndex(it) }?.takeIf { it >= 0 } ?: row.size
                    cellType = attributes.local("t").orEmpty()
                    cellStyle = attributes.local("s")?.toIntOrNull() ?: 0
                    value.setLength(0)
                }
                // <v> holds the stored value, <is><t> a text written inside the cell itself
                "v" -> inValue = true
                "t" -> inValue = cellType == "inlineStr"
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (plainName(localName, qName)) {
                "v", "t" -> inValue = false
                "c" -> if (column < MAX_COLUMNS) {
                    while (row.size < column) row.add("")
                    val text = cellText()
                    if (column < row.size) row[column] = text else row.add(text)
                }
                "row" -> if (rows.size < MAX_ROWS) rows.add(row)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inValue) value.append(ch, start, length)
        }

        private fun cellText(): String {
            val raw = value.toString()
            return when (cellType) {
                "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                "inlineStr", "str", "d" -> raw
                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                "e" -> ""
                else -> numberText(raw.trim())
            }
        }

        private fun numberText(raw: String): String {
            if (raw.isEmpty()) return ""
            val number = raw.toBigDecimalOrNull() ?: return raw
            if (dateStyles.getOrNull(cellStyle) == true) return serialToDate(number.toDouble(), epoch1904)
            // "20210001" instead of "2.0210001E7", "5" instead of "5.0"
            return if (number.compareTo(BigDecimal.ZERO) == 0) "0" else number.stripTrailingZeros().toPlainString()
        }
    }
}
