package vn.edu.actvn.kstudy.util

import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFRichTextString
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

// The workbooks are written with Apache POI, an independent implementation of the format,
// to prove that XlsxReader reads what real spreadsheet apps save.
class XlsxReaderTest {

    private fun bytesOf(workbook: Workbook): ByteArray {
        val out = ByteArrayOutputStream()
        workbook.use { it.write(out) }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = XlsxReader.read(ByteArrayInputStream(bytes))

    private fun readWorkbook(fill: (XSSFWorkbook) -> Unit): List<List<String>> {
        val workbook = XSSFWorkbook()
        fill(workbook)
        return read(bytesOf(workbook))
    }

    @Test
    fun read_returnsTheTextOfEveryCell() {
        val rows = readWorkbook { workbook ->
            val sheet = workbook.createSheet("Danh sách")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Họ và tên")
                createCell(1).setCellValue("Mã sinh viên")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("Nguyễn Thị Hồng Ánh")
                createCell(1).setCellValue("AT180101")
            }
        }

        assertEquals(
            listOf(listOf("Họ và tên", "Mã sinh viên"), listOf("Nguyễn Thị Hồng Ánh", "AT180101")),
            rows
        )
    }

    @Test
    fun read_numbers_comeBackAsAPersonReadsThem() {
        val rows = readWorkbook { workbook ->
            workbook.createSheet().createRow(0).apply {
                createCell(0).setCellValue(20210001.0) // a student code typed into a number cell
                createCell(1).setCellValue(912345678.0) // a phone number that lost its leading zero
                createCell(2).setCellValue(7.5)
                createCell(3).setCellValue(0.0)
                createCell(4).setCellValue(123456789012.0)
            }
        }

        assertEquals(listOf("20210001", "912345678", "7.5", "0", "123456789012"), rows[0])
    }

    @Test
    fun read_dateCells_comeBackAsDayMonthYear() {
        val rows = readWorkbook { workbook ->
            val builtIn = workbook.createCellStyle().apply { dataFormat = 14 }
            val custom = workbook.createCellStyle().apply {
                dataFormat = workbook.creationHelper.createDataFormat().getFormat("dd/mm/yyyy")
            }
            workbook.createSheet().createRow(0).apply {
                createCell(0).apply { setCellValue(LocalDate.of(2004, 3, 15)); cellStyle = builtIn }
                createCell(1).apply { setCellValue(LocalDate.of(2003, 12, 31)); cellStyle = custom }
                createCell(2).apply { setCellValue(LocalDate.of(2004, 2, 29)); cellStyle = custom }
                // The same kind of number without a date format stays a number
                createCell(3).setCellValue(38061.0)
            }
        }

        assertEquals(listOf("15/03/2004", "31/12/2003", "29/02/2004", "38061"), rows[0])
    }

    @Test
    fun read_keepsEmptyCellsAndEmptyRows_soRowsKeepTheirSpreadsheetNumbers() {
        val rows = readWorkbook { workbook ->
            val sheet = workbook.createSheet()
            sheet.createRow(0).createCell(0).setCellValue("DANH SÁCH LỚP")
            // Row 2 is left out of the file altogether
            sheet.createRow(2).apply {
                createCell(1).setCellValue("B3")
                createCell(3).setCellValue("D3")
            }
        }

        assertEquals(listOf(listOf("DANH SÁCH LỚP"), emptyList(), listOf("", "B3", "", "D3")), rows)
    }

    @Test
    fun read_readsOnlyTheFirstSheet() {
        val rows = readWorkbook { workbook ->
            workbook.createSheet("Danh sách").createRow(0).createCell(0).setCellValue("một")
            workbook.createSheet("Hướng dẫn").createRow(0).createCell(0).setCellValue("hai")
        }

        assertEquals(listOf(listOf("một")), rows)
    }

    @Test
    fun read_firstSheetIsTheFirstTab_notTheFirstFile() {
        val rows = readWorkbook { workbook ->
            workbook.createSheet("Ghi chú").createRow(0).createCell(0).setCellValue("ghi chú")
            workbook.createSheet("Danh sách").createRow(0).createCell(0).setCellValue("danh sách")
            // The sheet created second becomes the first tab; its part is still sheet2.xml
            workbook.setSheetOrder("Danh sách", 0)
        }

        assertEquals(listOf(listOf("danh sách")), rows)
    }

    @Test
    fun read_formulasBooleansAndStyledText() {
        val rows = readWorkbook { workbook ->
            val bold = workbook.createFont().apply { bold = true }
            val sheet = workbook.createSheet()
            sheet.createRow(0).apply {
                createCell(0).setCellValue(XSSFRichTextString("Nguyễn ").apply { append("Văn An", bold) })
                createCell(1).setCellValue(true)
                createCell(2).setCellValue(2.0)
                createCell(3).cellFormula = "C1*3"
                createCell(4).cellFormula = "\"AT\"&\"18A\""
            }
            // Stores the results next to the formulas, as a spreadsheet app does when it saves
            workbook.creationHelper.createFormulaEvaluator().evaluateAll()
        }

        assertEquals(listOf("Nguyễn Văn An", "TRUE", "2", "6", "AT18A"), rows[0])
    }

    @Test
    fun read_textWrittenInsideTheCells_asStreamingWritersDo() {
        // SXSSF writes inline strings instead of the shared string table
        val bytes = bytesOf(
            SXSSFWorkbook().apply {
                createSheet().createRow(0).apply {
                    createCell(0).setCellValue("Trần Thị Bình")
                    createCell(1).setCellValue("A & B <c>")
                }
            }
        )

        assertEquals(listOf(listOf("Trần Thị Bình", "A & B <c>")), read(bytes))
    }

    @Test
    fun read_workbookWrittenByXlsxWriter() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            listOf(
                XlsxSheet(
                    "Danh sách",
                    listOf(
                        listOf(XlsxCell.text("Họ và tên", XlsxStyle.HEADER), XlsxCell.text("Vắng", XlsxStyle.HEADER)),
                        listOf(XlsxCell.text("Lê Văn Cường"), XlsxCell.number(3)),
                        listOf(XlsxCell.text(""), XlsxCell.text("  có khoảng trắng  "))
                    )
                ),
                XlsxSheet("Khác", listOf(listOf(XlsxCell.text("bỏ qua"))))
            ),
            out
        )

        assertEquals(
            listOf(
                listOf("Họ và tên", "Vắng"),
                listOf("Lê Văn Cường", "3"),
                listOf("", "  có khoảng trắng  ")
            ),
            read(out.toByteArray())
        )
    }

    @Test
    fun read_templateOfTheStudentImport_givesItsHeaderRow() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(StudentImport.templateSheets(), out)

        val rows = read(out.toByteArray())

        assertEquals(1, rows.size)
        assertEquals(listOf("Họ và tên", "Mã sinh viên", "Lớp"), rows[0].take(3))
    }

    @Test
    fun read_zipThatIsNotAWorkbook_fails() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
            zip.write("không phải Excel".toByteArray())
            zip.closeEntry()
        }

        assertThrows(java.io.IOException::class.java) { read(out.toByteArray()) }
    }

    @Test
    fun columnIndex_convertsSpreadsheetLettersToAPosition() {
        assertEquals(0, XlsxReader.columnIndex("A1"))
        assertEquals(25, XlsxReader.columnIndex("Z9"))
        assertEquals(26, XlsxReader.columnIndex("AA10"))
        assertEquals(701, XlsxReader.columnIndex("ZZ1"))
        // The inverse of XlsxWriter.columnName
        for (index in listOf(0, 7, 26, 51, 52, 702)) {
            assertEquals(index, XlsxReader.columnIndex(XlsxWriter.columnName(index) + "1"))
        }
    }

    @Test
    fun serialToDate_countsDaysTheWayExcelDoes() {
        assertEquals("01/01/2000", XlsxReader.serialToDate(36526.0))
        assertEquals("15/03/2004", XlsxReader.serialToDate(38061.0))
        assertEquals("a time of day is ignored", "15/03/2004", XlsxReader.serialToDate(38061.75))
        assertEquals("01/01/2000", XlsxReader.serialToDate(35064.0, epoch1904 = true))
    }

    @Test
    fun isDateFormat_tellsDatesFromNumbers() {
        assertTrue(XlsxReader.isDateFormat(14, null))
        assertFalse("General", XlsxReader.isDateFormat(0, null))
        assertFalse("0.00", XlsxReader.isDateFormat(2, null))
        assertFalse("text", XlsxReader.isDateFormat(49, null))

        assertTrue(XlsxReader.isDateFormat(164, "dd/mm/yyyy"))
        assertTrue(XlsxReader.isDateFormat(165, "[\$-vi-VN]d mmmm yyyy"))
        assertFalse(XlsxReader.isDateFormat(166, "#,##0.00"))
        assertFalse("letters in quoted text do not count", XlsxReader.isDateFormat(167, "0 \"ngày\""))
        assertFalse("nor do colours", XlsxReader.isDateFormat(168, "[Red]0.0"))
    }
}
