package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.testutil.TestData

class AiPromptBuilderTest {

    private val classes = listOf(TestData.sampleClass(), TestData.emptyClass())

    // --- Student ---

    @Test
    fun studentData_containsOwnStatisticsPerClass() {
        val data = AiPromptBuilder.studentData("s3", classes)

        assertTrue(data.contains("Số lớp đang học: 2"))
        assertTrue(data.contains("Lớp \"Toán cao cấp A1\" (năm học 2025-2026)"))
        assertTrue(data.contains("Giáo viên: Nguyễn Văn An"))
        assertTrue(data.contains("Phòng: Phòng 101"))
        assertTrue(data.contains("Lịch học: Thứ 2 (07:30-09:00)"))
        assertTrue(data.contains("Số buổi đã chốt: 5"))
        assertTrue(data.contains("có mặt 2, muộn 1, vắng 2, tỷ lệ đi học 60%"))
        assertTrue(data.contains("Đánh giá: Nguy cơ cấm thi"))
    }

    @Test
    fun studentData_listsEverySessionInOrder() {
        val data = AiPromptBuilder.studentData("s3", classes)

        assertTrue(
            data.contains(
                "01/09/2026: Vắng; 08/09/2026 07:31: Vắng; 15/09/2026 07:32: Có mặt; " +
                    "22/09/2026 07:33: Muộn; 29/09/2026 07:30: Có mặt"
            )
        )
    }

    @Test
    fun studentData_neverLeaksClassmates() {
        val data = AiPromptBuilder.studentData("s3", classes)

        assertFalse(data.contains("Học sinh 1"))
        assertFalse(data.contains("Học sinh 2"))
        assertFalse(data.contains("SV001"))
        assertFalse(data.contains("SV002"))
    }

    @Test
    fun studentData_ignoresTheOpenSession() {
        val data = AiPromptBuilder.studentData("s1", classes)

        assertFalse(data.contains(TestData.OPEN_SESSION_DATE))
        assertTrue(data.contains("có mặt 5, muộn 0, vắng 0, tỷ lệ đi học 100%"))
    }

    @Test
    fun studentData_reportsExcusedAbsencesSeparately() {
        val base = TestData.sampleClass()
        val withApprovedLeave = base.copy(
            records = base.records.map {
                if (it.sessionId == "d1" && it.studentId == "s3") it.copy(status = "EXCUSED") else it
            }
        )

        val data = AiPromptBuilder.studentData("s3", listOf(withApprovedLeave))

        assertTrue(data.contains("có mặt 2, muộn 1, vắng 1, có phép 1, tỷ lệ đi học 60%"))
        assertTrue(data.contains("01/09/2026: Có phép; 08/09/2026 07:31: Vắng"))
        assertTrue(data.contains("Đánh giá: Cảnh báo chuyên cần"))
    }

    @Test
    fun studentData_classWithoutSessions_saysSo() {
        val data = AiPromptBuilder.studentData("s3", listOf(TestData.emptyClass()))

        assertTrue(data.contains("Lớp \"Anh văn B1\""))
        assertTrue(data.contains("Chưa có buổi điểm danh nào đã chốt."))
    }

    @Test
    fun studentData_withoutClasses_saysSo() {
        assertEquals("Học sinh chưa được xếp vào lớp học nào.", AiPromptBuilder.studentData("s3", emptyList()))
    }

    // --- Teacher ---

    @Test
    fun teacherData_containsEveryStudentWithCounts() {
        val data = AiPromptBuilder.teacherData(classes)

        assertTrue(data.contains("Số lớp đang dạy: 2"))
        assertTrue(data.contains("Sĩ số: 3; Số buổi đã chốt: 5"))
        assertTrue(data.contains("Tổng hợp cả lớp: có mặt 10, muộn 2, vắng 3, tỷ lệ đi học 80%."))
        assertTrue(data.contains("SV001 Học sinh 1: có mặt 5, muộn 0, vắng 0, tỷ lệ đi học 100%; Đạt yêu cầu"))
        assertTrue(data.contains("SV002 Học sinh 2: có mặt 3, muộn 1, vắng 1, tỷ lệ đi học 80%; Cảnh báo chuyên cần"))
        assertTrue(data.contains("SV003 Học sinh 3: có mặt 2, muộn 1, vắng 2, tỷ lệ đi học 60%; Nguy cơ cấm thi"))
    }

    @Test
    fun teacherData_namesAbsentAndLateStudentsPerSession() {
        val data = AiPromptBuilder.teacherData(classes)

        assertTrue(
            data.contains("01/09/2026: có mặt 2, muộn 0, vắng 1, tỷ lệ đi học 67%. Vắng: Học sinh 3. Muộn: không có.")
        )
        assertTrue(
            data.contains("08/09/2026 07:31: có mặt 1, muộn 1, vắng 1, tỷ lệ đi học 67%. Vắng: Học sinh 3. Muộn: Học sinh 2.")
        )
        assertTrue(
            data.contains("29/09/2026 07:30: có mặt 3, muộn 0, vắng 0, tỷ lệ đi học 100%. Vắng: không có. Muộn: không có.")
        )
    }

    @Test
    fun teacherData_ignoresTheOpenSession() {
        assertFalse(AiPromptBuilder.teacherData(classes).contains(TestData.OPEN_SESSION_DATE))
    }

    @Test
    fun teacherData_classWithoutSessions_saysSo() {
        val data = AiPromptBuilder.teacherData(listOf(TestData.emptyClass()))

        assertTrue(data.contains("Lớp \"Anh văn B1\""))
        assertTrue(data.contains("Chưa có buổi điểm danh nào đã chốt."))
    }

    @Test
    fun teacherData_withoutClasses_saysSo() {
        assertEquals("Giáo viên chưa được phân công lớp học nào.", AiPromptBuilder.teacherData(emptyList()))
    }

    // --- Admin ---

    @Test
    fun adminData_summarisesTheSystemAndEachClass() {
        val data = AiPromptBuilder.adminData(classes)

        assertTrue(data.contains("Toàn hệ thống: 2 lớp học. có mặt 10, muộn 2, vắng 3, tỷ lệ đi học 80%."))
        assertTrue(data.contains("Lớp \"Toán cao cấp A1\""))
        assertTrue(data.contains("Giáo viên: Nguyễn Văn An"))
        assertTrue(data.contains("Lớp \"Anh văn B1\""))
        assertTrue(data.contains("Giáo viên: Trần Thị Bình"))
    }

    @Test
    fun adminData_listsStudentsAtRiskMostSeriousFirst() {
        val data = AiPromptBuilder.adminData(classes)

        assertTrue(
            data.contains(
                "Học sinh cần lưu ý: Học sinh 3 (SV003) vắng 2/5 buổi (40%) - Nguy cơ cấm thi; " +
                    "Học sinh 2 (SV002) vắng 1/5 buổi (20%) - Cảnh báo chuyên cần"
            )
        )
        assertTrue(data.contains("Học sinh cần lưu ý: không có."))
    }

    @Test
    fun adminData_withoutClasses_saysSo() {
        assertEquals("Hệ thống chưa có lớp học nào.", AiPromptBuilder.adminData(emptyList()))
    }

    // --- Instructions ---

    @Test
    fun chatSystemInstruction_describesUserRulesAndData() {
        val instruction = AiPromptBuilder.chatSystemInstruction(
            role = Role.STUDENT,
            userName = "Lê Minh Châu",
            today = "Chủ Nhật, 11/10/2026",
            data = "DỮ-LIỆU-MẪU"
        )

        assertTrue(instruction.contains("học sinh Lê Minh Châu"))
        assertTrue(instruction.contains("Hôm nay là Chủ Nhật, 11/10/2026"))
        assertTrue(instruction.contains("vắng quá 20% số buổi"))
        assertTrue(instruction.contains("vắng quá 10% là mức cảnh báo"))
        assertTrue(instruction.contains("\"Có phép\" là buổi vắng có đơn xin nghỉ đã được giáo viên duyệt"))
        assertTrue(instruction.contains("không tự bịa số liệu"))
        assertTrue(instruction.contains("không dùng Markdown"))
        assertTrue(instruction.endsWith("DỮ LIỆU ĐIỂM DANH:\nDỮ-LIỆU-MẪU"))
    }

    @Test
    fun chatSystemInstruction_namesTheRole() {
        fun instruction(role: Role) = AiPromptBuilder.chatSystemInstruction(role, "An", "hôm nay", "x")

        assertTrue(instruction(Role.TEACHER).contains("giáo viên An"))
        assertTrue(instruction(Role.ADMIN).contains("quản trị viên An"))
        assertTrue(instruction(Role.STUDENT).contains("học sinh An"))
    }

    @Test
    fun chatSystemInstruction_blankName_doesNotLeaveAGap() {
        val instruction = AiPromptBuilder.chatSystemInstruction(Role.TEACHER, " ", "hôm nay", "x")

        assertTrue(instruction.contains("giáo viên (chưa rõ tên)"))
    }

    @Test
    fun classAnalysisPrompt_asksForThreePartsAndIncludesTheClassData() {
        val prompt = AiPromptBuilder.classAnalysisPrompt(AttendanceAnalyzer.analyzeClass(TestData.sampleClass()))

        assertTrue(prompt.contains("1. Tổng quan"))
        assertTrue(prompt.contains("2. Học sinh cần lưu ý"))
        assertTrue(prompt.contains("3. Đề xuất"))
        assertTrue(prompt.contains("Lớp \"Toán cao cấp A1\""))
        assertTrue(prompt.contains("SV003 Học sinh 3: có mặt 2, muộn 1, vắng 2"))
        assertTrue(prompt.contains("Tổng hợp cả lớp: có mặt 10, muộn 2, vắng 3, tỷ lệ đi học 80%."))
    }

    @Test
    fun classAnalysisInstruction_statesTheAttendanceRule() {
        val instruction = AiPromptBuilder.classAnalysisInstruction()

        assertTrue(instruction.contains("vắng quá 20% số buổi"))
        assertTrue(instruction.contains("không dùng Markdown"))
    }

    // --- cleanReply ---

    @Test
    fun cleanReply_removesBoldCodeAndHeadings() {
        val raw = "## Tổng quan\n**Lớp** đi học `80%`.\n__Tốt__"

        assertEquals("Tổng quan\nLớp đi học 80%.\nTốt", AiPromptBuilder.cleanReply(raw))
    }

    @Test
    fun cleanReply_turnsStarBulletsIntoDashes() {
        val raw = "Danh sách:\n* Học sinh 3\n  * vắng 2 buổi\n• Học sinh 2\n- giữ nguyên"

        assertEquals(
            "Danh sách:\n- Học sinh 3\n  - vắng 2 buổi\n- Học sinh 2\n- giữ nguyên",
            AiPromptBuilder.cleanReply(raw)
        )
    }

    @Test
    fun cleanReply_collapsesBlankLinesAndTrims() {
        assertEquals("a\n\nb", AiPromptBuilder.cleanReply("\n\n a\n\n\n\nb \n"))
    }

    @Test
    fun cleanReply_keepsPlainTextUntouched() {
        val plain = "Bạn đã vắng 2/5 buổi (40%), 3 * 4 = 12."

        assertEquals(plain, AiPromptBuilder.cleanReply(plain))
    }

    @Test
    fun cleanReply_onlyMarkdown_becomesEmpty() {
        assertEquals("", AiPromptBuilder.cleanReply(" ** \n"))
    }
}
