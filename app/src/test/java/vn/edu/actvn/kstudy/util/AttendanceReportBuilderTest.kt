package vn.edu.actvn.kstudy.util

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.testutil.TestData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AttendanceReportBuilderTest {

    private val exportedAt = "11/10/2026 15:30"
    private val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())
    private val sheets = AttendanceReportBuilder.build(report, exportedAt)
    private val roster = sheets[0]
    private val bySession = sheets[1]

    // Row index of the table header on the roster sheet (after title, two info lines and a blank row)
    private val headerRow = 4

    private fun XlsxSheet.texts(row: Int): List<String> =
        rows[row].map { cell -> cell.number?.toInt()?.toString() ?: cell.text }

    @Test
    fun build_createsRosterAndSessionSheets() {
        assertEquals(listOf("Điểm danh", "Theo buổi"), sheets.map { it.name })
    }

    @Test
    fun rosterSheet_startsWithClassInformation() {
        assertEquals("BẢNG ĐIỂM DANH - Toán cao cấp A1", roster.rows[0][0].text)
        assertEquals(XlsxStyle.TITLE, roster.rows[0][0].style)

        val info = roster.rows[1][0].text + " " + roster.rows[2][0].text
        assertTrue(info.contains("Năm học: 2025-2026"))
        assertTrue(info.contains("Phòng: Phòng 101"))
        assertTrue(info.contains("Lịch học: Thứ 2 (07:30-09:00)"))
        assertTrue(info.contains("Giáo viên: Nguyễn Văn An"))
        assertTrue(info.contains("Sĩ số: 3"))
        assertTrue(info.contains("Số buổi đã chốt: 5"))
        assertTrue(info.contains("Ngày xuất: $exportedAt"))
    }

    @Test
    fun rosterSheet_headerHasOneColumnPerClosedSession() {
        assertEquals(
            listOf(
                "STT", "Mã SV", "Họ và tên",
                "01/09/2026", "08/09/2026", "15/09/2026", "22/09/2026", "29/09/2026",
                "Có mặt", "Muộn", "Vắng", "Có phép", "Chuyên cần (%)", "Điểm chuyên cần", "Ghi chú"
            ),
            roster.texts(headerRow)
        )
        assertTrue(roster.rows[headerRow].all { it.style == XlsxStyle.HEADER })
    }

    @Test
    fun rosterSheet_doesNotShowTheOpenSession() {
        assertFalse(roster.texts(headerRow).contains("06/10/2026"))
    }

    @Test
    fun rosterSheet_hasOneRowPerStudentWithStatusesAndTotals() {
        assertEquals(
            listOf("1", "SV001", "Học sinh 1", "Có mặt", "Có mặt", "Có mặt", "Có mặt", "Có mặt", "5", "0", "0", "0", "100", "10", ""),
            roster.texts(headerRow + 1)
        )
        assertEquals(
            listOf("2", "SV002", "Học sinh 2", "Có mặt", "Muộn", "Vắng", "Có mặt", "Có mặt", "3", "1", "1", "0", "80", "9", "Cảnh báo"),
            roster.texts(headerRow + 2)
        )
        assertEquals(
            listOf("3", "SV003", "Học sinh 3", "Vắng", "Vắng", "Có mặt", "Muộn", "Có mặt", "2", "1", "2", "0", "60", "8", "Nguy cơ cấm thi"),
            roster.texts(headerRow + 3)
        )
    }

    @Test
    fun rosterSheet_coloursStatusAndRiskCells() {
        val row = roster.rows[headerRow + 3] // SV003

        assertEquals(XlsxStyle.ABSENT, row[3].style)
        assertEquals(XlsxStyle.PRESENT, row[5].style)
        assertEquals(XlsxStyle.LATE, row[6].style)
        assertEquals(XlsxStyle.DANGER, row.last().style)
        assertEquals(XlsxStyle.WARNING, roster.rows[headerRow + 2].last().style)
    }

    @Test
    fun rosterSheet_showsExcusedAbsencesInTheirOwnColumn() {
        val base = TestData.sampleClass()
        val data = base.copy(
            records = base.records.map {
                if (it.sessionId == "d1" && it.studentId == "s3") it.copy(status = "EXCUSED") else it
            }
        )

        val sheet = AttendanceReportBuilder.build(AttendanceAnalyzer.analyzeClass(data), exportedAt)[0]

        assertEquals(
            listOf("3", "SV003", "Học sinh 3", "Có phép", "Vắng", "Có mặt", "Muộn", "Có mặt", "2", "1", "1", "1", "60", "9", "Cảnh báo"),
            sheet.texts(headerRow + 3)
        )
        assertEquals(XlsxStyle.EXCUSED, sheet.rows[headerRow + 3][3].style)
    }

    @Test
    fun rosterSheet_endsWithAttendanceRatePerSession() {
        assertEquals(
            listOf("", "", "Tỷ lệ đi học theo buổi (%)", "67", "67", "67", "100", "100"),
            roster.texts(roster.rows.lastIndex)
        )
    }

    @Test
    fun rosterSheet_definesAWidthForEveryColumn() {
        assertEquals(roster.rows[headerRow].size, roster.columnWidths.size)
    }

    @Test
    fun rosterSheet_studentWithoutRecordGetsDash() {
        val base = TestData.sampleClass()
        val newcomer = TestData.student(4)
        val data = base.copy(
            students = base.students + newcomer,
            records = base.records + TestData.record("d5", newcomer.id, "PRESENT")
        )

        val sheet = AttendanceReportBuilder.build(AttendanceAnalyzer.analyzeClass(data), exportedAt)[0]

        assertEquals(
            listOf("4", "SV004", "Học sinh 4", "-", "-", "-", "-", "Có mặt", "1", "0", "0", "0", "100", "10", ""),
            sheet.texts(headerRow + 4)
        )
    }

    @Test
    fun sessionSheet_listsEverySessionWithCounts() {
        assertEquals(
            listOf("STT", "Ngày điểm danh", "Có mặt", "Muộn", "Vắng", "Có phép", "Tỷ lệ đi học (%)"),
            bySession.texts(2)
        )
        assertEquals(listOf("1", "01/09/2026", "2", "0", "1", "0", "67"), bySession.texts(3))
        assertEquals(listOf("2", "08/09/2026 07:31", "1", "1", "1", "0", "67"), bySession.texts(4))
        assertEquals(listOf("5", "29/09/2026 07:30", "3", "0", "0", "0", "100"), bySession.texts(7))
        assertEquals(8, bySession.rows.size)
    }

    @Test
    fun build_classWithoutSessions_stillProducesAValidRoster() {
        val emptySheets = AttendanceReportBuilder.build(AttendanceAnalyzer.analyzeClass(TestData.emptyClass()), exportedAt)

        assertEquals(
            listOf("STT", "Mã SV", "Họ và tên", "Có mặt", "Muộn", "Vắng", "Có phép", "Chuyên cần (%)", "Điểm chuyên cần", "Ghi chú"),
            emptySheets[0].texts(headerRow)
        )
        assertEquals(
            listOf("1", "SV003", "Học sinh 3", "0", "0", "0", "0", "-", "10", ""),
            emptySheets[0].texts(headerRow + 1)
        )
        assertEquals("no per-session footer without sessions", headerRow + 2, emptySheets[0].rows.size)
        assertEquals(3, emptySheets[1].rows.size)
    }

    @Test
    fun build_resultCanBeWrittenAndOpenedAsARealWorkbook() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sheets, out)

        XSSFWorkbook(ByteArrayInputStream(out.toByteArray())).use { workbook ->
            val sheet = workbook.getSheet("Điểm danh")
            val danger = sheet.getRow(headerRow + 3)

            assertEquals("Học sinh 3", danger.getCell(2).stringCellValue)
            assertEquals("Vắng", danger.getCell(3).stringCellValue)
            assertEquals(CellType.NUMERIC, danger.getCell(10).cellType)
            assertEquals(2.0, danger.getCell(10).numericCellValue, 0.0)
            assertEquals(60.0, danger.getCell(12).numericCellValue, 0.0)
            assertEquals(8.0, danger.getCell(13).numericCellValue, 0.0)
            assertEquals("Nguy cơ cấm thi", danger.getCell(14).stringCellValue)
            assertEquals(5, workbook.getSheet("Theo buổi").lastRowNum - 2)
        }
    }

    @Test
    fun fileName_isAsciiAndContainsClassAndTimestamp() {
        assertEquals(
            "DiemDanh_Toan-cao-cap-A1_20261011_1530.xlsx",
            AttendanceReportBuilder.fileName("Toán cao cấp A1", "20261011_1530")
        )
    }

    @Test
    fun fileName_handlesVietnameseLetterD() {
        assertEquals(
            "DiemDanh_Dai-so-va-duong-loi_20261011_1530.xlsx",
            AttendanceReportBuilder.fileName("Đại số và đường lối", "20261011_1530")
        )
    }

    @Test
    fun fileName_dropsCharactersThatAreUnsafeInFileNames() {
        val name = AttendanceReportBuilder.fileName("Lớp 10/2: \"Lý\" <nâng cao>?", "20261011_1530")

        assertEquals("DiemDanh_Lop-10-2-Ly-nang-cao_20261011_1530.xlsx", name)
    }

    @Test
    fun fileName_blankClassName_fallsBackToDefault() {
        assertEquals("DiemDanh_Lop_20261011_1530.xlsx", AttendanceReportBuilder.fileName("  ", "20261011_1530"))
    }

    @Test
    fun fileName_veryLongClassName_isShortened() {
        val name = AttendanceReportBuilder.fileName("Lập trình ".repeat(30), "20261011_1530")

        assertTrue(name.length <= "DiemDanh_".length + 40 + "_20261011_1530.xlsx".length)
        assertFalse(name.contains("-_"))
    }
}
