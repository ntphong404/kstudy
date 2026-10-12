package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus
import java.text.SimpleDateFormat
import java.util.Locale

// Rules of the leave request workflow, kept free of Firestore so they can be unit tested
object LeaveRequestRules {

    const val DATE_PATTERN = "dd/MM/yyyy"

    // Says what is wrong with a new request, or returns null when it can be sent.
    // `existing` are the requests this student already made for the same class.
    fun problemWith(date: String, reason: String, existing: List<LeaveRequest>): String? = when {
        !isValidDate(date) -> "Vui lòng chọn ngày xin nghỉ!"
        reason.isBlank() -> "Vui lòng nhập lý do xin nghỉ!"
        // A rejected request may be sent again, e.g. with a better reason
        existing.any { it.date == date && it.status != LeaveStatus.REJECTED } ->
            "Bạn đã gửi đơn xin nghỉ cho ngày này rồi!"
        else -> null
    }

    // Requests still waiting for a decision first, then the most recent
    fun sorted(requests: List<LeaveRequest>): List<LeaveRequest> =
        requests.sortedWith(
            compareBy<LeaveRequest> { it.status != LeaveStatus.PENDING }.thenByDescending { it.createdAt }
        )

    // Approving a request excuses an absence; a student who came to class anyway keeps the real status
    fun statusAfterApproval(current: String): String =
        if (current == AttendanceStatus.ABSENT) AttendanceStatus.EXCUSED else current

    // Status of a student's record when a session is opened, before anybody has scanned
    fun initialStatus(studentId: String, excusedStudentIds: Set<String>): String =
        if (studentId in excusedStudentIds) AttendanceStatus.EXCUSED else AttendanceStatus.ABSENT

    private fun isValidDate(date: String): Boolean {
        if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(date)) return false
        return try {
            val format = SimpleDateFormat(DATE_PATTERN, Locale.US)
            format.isLenient = false
            format.parse(date) != null
        } catch (e: Exception) {
            false
        }
    }
}
