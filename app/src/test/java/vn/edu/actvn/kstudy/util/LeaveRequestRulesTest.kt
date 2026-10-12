package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.ABSENT
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.EXCUSED
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.LATE
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.PRESENT
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus

class LeaveRequestRulesTest {

    private fun request(id: String, date: String, status: String, createdAt: Long = 0L) =
        LeaveRequest(id = id, classId = "c1", studentId = "s1", date = date, reason = "Ốm", status = status, createdAt = createdAt)

    // --- problemWith ---

    @Test
    fun problemWith_validNewRequest_hasNoProblem() {
        assertNull(LeaveRequestRules.problemWith("12/10/2026", "Em bị sốt", emptyList()))
    }

    @Test
    fun problemWith_missingOrMalformedDate_asksForADate() {
        val expected = "Vui lòng chọn ngày xin nghỉ!"

        assertEquals(expected, LeaveRequestRules.problemWith("", "Em bị sốt", emptyList()))
        assertEquals(expected, LeaveRequestRules.problemWith("2026-10-12", "Em bị sốt", emptyList()))
        assertEquals(expected, LeaveRequestRules.problemWith("12/10/26", "Em bị sốt", emptyList()))
        assertEquals("a day that does not exist", expected, LeaveRequestRules.problemWith("31/02/2026", "Em bị sốt", emptyList()))
    }

    @Test
    fun problemWith_blankReason_asksForAReason() {
        assertEquals("Vui lòng nhập lý do xin nghỉ!", LeaveRequestRules.problemWith("12/10/2026", "   ", emptyList()))
    }

    @Test
    fun problemWith_sameDayAlreadyPendingOrApproved_isADuplicate() {
        val expected = "Bạn đã gửi đơn xin nghỉ cho ngày này rồi!"

        assertEquals(
            expected,
            LeaveRequestRules.problemWith("12/10/2026", "Ốm", listOf(request("r1", "12/10/2026", LeaveStatus.PENDING)))
        )
        assertEquals(
            expected,
            LeaveRequestRules.problemWith("12/10/2026", "Ốm", listOf(request("r1", "12/10/2026", LeaveStatus.APPROVED)))
        )
    }

    @Test
    fun problemWith_rejectedRequest_mayBeSentAgain() {
        val existing = listOf(request("r1", "12/10/2026", LeaveStatus.REJECTED))

        assertNull(LeaveRequestRules.problemWith("12/10/2026", "Em có giấy của bác sĩ", existing))
    }

    @Test
    fun problemWith_requestForAnotherDay_isNotADuplicate() {
        val existing = listOf(request("r1", "05/10/2026", LeaveStatus.APPROVED))

        assertNull(LeaveRequestRules.problemWith("12/10/2026", "Ốm", existing))
    }

    // --- sorted ---

    @Test
    fun sorted_putsPendingFirstThenNewest() {
        val requests = listOf(
            request("old-approved", "01/10/2026", LeaveStatus.APPROVED, createdAt = 100),
            request("old-pending", "02/10/2026", LeaveStatus.PENDING, createdAt = 200),
            request("new-rejected", "03/10/2026", LeaveStatus.REJECTED, createdAt = 400),
            request("new-pending", "04/10/2026", LeaveStatus.PENDING, createdAt = 300)
        )

        assertEquals(
            listOf("new-pending", "old-pending", "new-rejected", "old-approved"),
            LeaveRequestRules.sorted(requests).map { it.id }
        )
    }

    // --- Effect on attendance records ---

    @Test
    fun statusAfterApproval_excusesAnAbsence() {
        assertEquals(EXCUSED, LeaveRequestRules.statusAfterApproval(ABSENT))
    }

    @Test
    fun statusAfterApproval_keepsTheStatusOfAStudentWhoCameAnyway() {
        assertEquals(PRESENT, LeaveRequestRules.statusAfterApproval(PRESENT))
        assertEquals(LATE, LeaveRequestRules.statusAfterApproval(LATE))
        assertEquals(EXCUSED, LeaveRequestRules.statusAfterApproval(EXCUSED))
    }

    @Test
    fun initialStatus_isExcusedOnlyForStudentsWithAnApprovedRequest() {
        val excused = setOf("s2")

        assertEquals(ABSENT, LeaveRequestRules.initialStatus("s1", excused))
        assertEquals(EXCUSED, LeaveRequestRules.initialStatus("s2", excused))
        assertEquals(ABSENT, LeaveRequestRules.initialStatus("s1", emptySet()))
    }
}
