package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class SpreadsheetReaderTest {

    private val expected = listOf(listOf("Họ và tên", "Lớp"), listOf("Nguyễn Thị Hồng Ánh", "AT18A"))
    private val csv = "Họ và tên,Lớp\nNguyễn Thị Hồng Ánh,AT18A\n"

    private fun failure(bytes: ByteArray): String? =
        assertThrows(ImportFileException::class.java) { SpreadsheetReader.read(bytes) }.message

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun read_csvInUtf8_withOrWithoutAByteOrderMark() {
        assertEquals(expected, SpreadsheetReader.read(csv.toByteArray(Charsets.UTF_8)))
        assertEquals(expected, SpreadsheetReader.read(bytes(0xEF, 0xBB, 0xBF) + csv.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun read_unicodeTextOfExcel_isUtf16() {
        val tabbed = csv.replace(',', '\t')

        assertEquals(expected, SpreadsheetReader.read(bytes(0xFF, 0xFE) + tabbed.toByteArray(Charsets.UTF_16LE)))
        assertEquals(expected, SpreadsheetReader.read(bytes(0xFE, 0xFF) + tabbed.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun read_plainCsvOfExcelOnVietnameseWindows_isReadWithItsCodePage() {
        // ê, ă, ơ and Đ are single bytes in windows-1258, and those bytes are not valid UTF-8
        val text = "Ho va ten,Lop\nLê Văn Sơn,Đ18A\n"

        val rows = SpreadsheetReader.read(text.toByteArray(Charset.forName("windows-1258")))

        assertEquals(listOf(listOf("Ho va ten", "Lop"), listOf("Lê Văn Sơn", "Đ18A")), rows)
    }

    @Test
    fun read_xlsx_isToldFromItsContent() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            listOf(XlsxSheet("S", expected.map { row -> row.map { XlsxCell.text(it) } })),
            out
        )

        assertEquals(expected, SpreadsheetReader.read(out.toByteArray()))
        assertEquals("from a stream", expected, SpreadsheetReader.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun read_emptyFile_isRefused() {
        assertEquals(SpreadsheetReader.EMPTY_FILE, failure(ByteArray(0)))
    }

    @Test
    fun read_oldExcelWorkbook_asksForANewerFormat() {
        val xls = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) + ByteArray(64)

        assertEquals(SpreadsheetReader.OLD_EXCEL, failure(xls))
    }

    @Test
    fun read_zipThatIsNotAWorkbook_isReportedAsBroken() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("x".toByteArray())
            zip.closeEntry()
        }

        assertEquals(SpreadsheetReader.BROKEN_EXCEL, failure(out.toByteArray()))
    }

    @Test
    fun read_binaryFile_isNotASpreadsheet() {
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D)

        assertEquals(SpreadsheetReader.NOT_A_SPREADSHEET, failure(png))
    }

    @Test
    fun read_streamLargerThanTheLimit_isRefusedWithoutReadingItAll() {
        val endless = object : java.io.InputStream() {
            var served = 0L
            override fun read(): Int {
                served++
                return 'a'.code
            }
        }

        val error = assertThrows(ImportFileException::class.java) { SpreadsheetReader.read(endless) }

        assertEquals(SpreadsheetReader.TOO_LARGE, error.message)
        assertTrue(endless.served < SpreadsheetReader.MAX_FILE_BYTES * 2L)
    }
}
