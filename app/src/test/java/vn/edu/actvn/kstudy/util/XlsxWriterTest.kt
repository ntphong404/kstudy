package vn.edu.actvn.kstudy.util

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

// The files are read back with Apache POI, an independent implementation of the format,
// to prove that what XlsxWriter produces is a workbook real spreadsheet apps can open.
class XlsxWriterTest {

    private fun write(vararg sheets: XlsxSheet): ByteArray {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets.toList(), out)
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray) = XSSFWorkbook(ByteArrayInputStream(bytes))

    @Test
    fun write_producesWorkbookWithTextAndNumbers() {
        val bytes = write(
            XlsxSheet(
                name = "Điểm danh",
                rows = listOf(
                    listOf(XlsxCell.text("Họ và tên", XlsxStyle.HEADER), XlsxCell.text("Vắng", XlsxStyle.HEADER)),
                    listOf(XlsxCell.text("Nguyễn Thị Hồng Ánh"), XlsxCell.number(3))
                )
            )
        )

        open(bytes).use { workbook ->
            assertEquals(1, workbook.numberOfSheets)
            val sheet = workbook.getSheet("Điểm danh")
            assertNotNull(sheet)
            assertEquals("Họ và tên", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("Vắng", sheet.getRow(0).getCell(1).stringCellValue)
            assertEquals("Nguyễn Thị Hồng Ánh", sheet.getRow(1).getCell(0).stringCellValue)

            val numberCell = sheet.getRow(1).getCell(1)
            assertEquals(CellType.NUMERIC, numberCell.cellType)
            assertEquals(3.0, numberCell.numericCellValue, 0.0)
        }
    }

    @Test
    fun write_keepsSpecialCharactersIntact() {
        val tricky = "A & B <c> \"d\" 'e' 100% ; =SUM(1,2)"
        val bytes = write(XlsxSheet("S", listOf(listOf(XlsxCell.text(tricky), XlsxCell.text("  có khoảng trắng  ")))))

        open(bytes).use { workbook ->
            val row = workbook.getSheetAt(0).getRow(0)
            assertEquals(tricky, row.getCell(0).stringCellValue)
            assertEquals("  có khoảng trắng  ", row.getCell(1).stringCellValue)
            assertEquals("text must never be turned into a formula", CellType.STRING, row.getCell(0).cellType)
        }
    }

    @Test
    fun write_dropsControlCharactersThatWouldCorruptTheFile() {
        val bytes = write(XlsxSheet("S", listOf(listOf(XlsxCell.text("a\u0000b\u0007c")))))

        open(bytes).use { workbook ->
            assertEquals("abc", workbook.getSheetAt(0).getRow(0).getCell(0).stringCellValue)
        }
    }

    @Test
    fun write_supportsSeveralSheetsInOrder() {
        val bytes = write(
            XlsxSheet("Điểm danh", listOf(listOf(XlsxCell.text("một")))),
            XlsxSheet("Theo buổi", listOf(listOf(XlsxCell.text("hai"))))
        )

        open(bytes).use { workbook ->
            assertEquals(2, workbook.numberOfSheets)
            assertEquals("Điểm danh", workbook.getSheetName(0))
            assertEquals("Theo buổi", workbook.getSheetName(1))
            assertEquals("một", workbook.getSheetAt(0).getRow(0).getCell(0).stringCellValue)
            assertEquals("hai", workbook.getSheetAt(1).getRow(0).getCell(0).stringCellValue)
        }
    }

    @Test
    fun write_reusesSharedStrings() {
        val row = listOf(XlsxCell.text("Có mặt"), XlsxCell.text("Có mặt"), XlsxCell.text("Vắng"))
        val bytes = write(XlsxSheet("S", listOf(row, row)))

        open(bytes).use { workbook ->
            assertEquals(2, workbook.sharedStringSource.uniqueCount)
            assertEquals("Vắng", workbook.getSheetAt(0).getRow(1).getCell(2).stringCellValue)
        }
    }

    @Test
    fun write_emptyCellsAndEmptyRowsAreAllowed() {
        val bytes = write(
            XlsxSheet(
                "S",
                listOf(
                    listOf(XlsxCell.text("tiêu đề")),
                    emptyList(),
                    listOf(XlsxCell.text("", XlsxStyle.BODY), XlsxCell.text("x"))
                )
            )
        )

        open(bytes).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            assertEquals("tiêu đề", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("", sheet.getRow(2).getCell(0).stringCellValue)
            assertEquals("x", sheet.getRow(2).getCell(1).stringCellValue)
        }
    }

    @Test
    fun write_appliesHeaderStyle() {
        val bytes = write(XlsxSheet("S", listOf(listOf(XlsxCell.text("Mã SV", XlsxStyle.HEADER)))))

        open(bytes).use { workbook ->
            val style = workbook.getSheetAt(0).getRow(0).getCell(0).cellStyle as XSSFCellStyle
            assertTrue(style.font.bold)
            assertEquals("FF5B21B6", style.fillForegroundColorColor.argbHex)
            assertEquals(HorizontalAlignment.CENTER, style.alignment)
        }
    }

    @Test
    fun write_appliesStatusColours() {
        val bytes = write(
            XlsxSheet(
                "S",
                listOf(
                    listOf(
                        XlsxCell.text("Có mặt", XlsxStyle.PRESENT),
                        XlsxCell.text("Muộn", XlsxStyle.LATE),
                        XlsxCell.text("Vắng", XlsxStyle.ABSENT),
                        XlsxCell.text("Nguy cơ cấm thi", XlsxStyle.DANGER),
                        XlsxCell.text("thường", XlsxStyle.BODY)
                    )
                )
            )
        )

        open(bytes).use { workbook ->
            val row = workbook.getSheetAt(0).getRow(0)
            fun style(col: Int) = row.getCell(col).cellStyle as XSSFCellStyle

            assertEquals("FFE8F5E9", style(0).fillForegroundColorColor.argbHex)
            assertEquals("FFFFF3C4", style(1).fillForegroundColorColor.argbHex)
            assertEquals("FFFFEBEE", style(2).fillForegroundColorColor.argbHex)
            assertEquals("FFFFEBEE", style(3).fillForegroundColorColor.argbHex)
            assertTrue(style(3).font.bold)
            assertFalse(style(4).font.bold)
        }
    }

    @Test
    fun write_appliesColumnWidths() {
        val bytes = write(
            XlsxSheet("S", listOf(listOf(XlsxCell.text("a"), XlsxCell.text("b"))), columnWidths = listOf(6, 28))
        )

        open(bytes).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            // POI reports widths in 1/256 of a character
            assertEquals(6 * 256, sheet.getColumnWidth(0))
            assertEquals(28 * 256, sheet.getColumnWidth(1))
        }
    }

    @Test
    fun write_makesInvalidOrDuplicateSheetNamesUsable() {
        val bytes = write(
            XlsxSheet("Lớp 10/2: [A]", listOf(listOf(XlsxCell.text("1")))),
            XlsxSheet("Lớp 10/2: [A]", listOf(listOf(XlsxCell.text("2")))),
            XlsxSheet("", listOf(listOf(XlsxCell.text("3"))))
        )

        open(bytes).use { workbook ->
            val names = (0 until workbook.numberOfSheets).map { workbook.getSheetName(it) }
            assertEquals(3, names.toSet().size)
            assertTrue(names.all { it.isNotEmpty() && it.length <= 31 })
            assertTrue(names.none { name -> name.any { it in "\\/?*[]:" } })
        }
    }

    @Test
    fun write_withoutSheets_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            XlsxWriter.write(emptyList(), ByteArrayOutputStream())
        }
    }

    @Test
    fun write_containsThePartsRequiredByTheFileFormat() {
        val bytes = write(XlsxSheet("S", listOf(listOf(XlsxCell.text("a")))))

        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zip.nextEntry
            }
        }

        assertEquals("[Content_Types].xml", entries.first())
        assertTrue(
            entries.containsAll(
                listOf(
                    "_rels/.rels",
                    "xl/workbook.xml",
                    "xl/_rels/workbook.xml.rels",
                    "xl/styles.xml",
                    "xl/sharedStrings.xml",
                    "xl/worksheets/sheet1.xml"
                )
            )
        )
    }

    @Test
    fun columnName_convertsIndexToSpreadsheetLetters() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
        assertEquals("BA", XlsxWriter.columnName(52))
        assertEquals("ZZ", XlsxWriter.columnName(701))
        assertEquals("AAA", XlsxWriter.columnName(702))
    }

    @Test
    fun write_supportsMoreThan26Columns() {
        val wide = (0 until 40).map { XlsxCell.number(it) }
        val bytes = write(XlsxSheet("S", listOf(wide)))

        open(bytes).use { workbook ->
            val row = workbook.getSheetAt(0).getRow(0)
            assertEquals(40, row.lastCellNum.toInt())
            assertEquals(39.0, row.getCell(39).numericCellValue, 0.0)
        }
    }

    @Test
    fun sanitizeSheetName_removesForbiddenCharactersAndLimitsLength() {
        assertEquals("Lớp 10 2   A", XlsxWriter.sanitizeSheetName("Lớp 10/2: [A]"))
        assertEquals("Sheet", XlsxWriter.sanitizeSheetName("  "))
        assertEquals(31, XlsxWriter.sanitizeSheetName("x".repeat(50)).length)
    }

    @Test
    fun escapeXml_escapesMarkupCharacters() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot;", XlsxWriter.escapeXml("a & b <c> \"d\""))
        assertEquals("tiếng Việt", XlsxWriter.escapeXml("tiếng Việt"))
    }
}
