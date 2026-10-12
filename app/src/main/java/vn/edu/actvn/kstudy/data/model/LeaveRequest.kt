package vn.edu.actvn.kstudy.data.model

// A student asking to be excused from one lesson of a class (`leaveRequests` collection)
data class LeaveRequest(
    val id: String = "",
    val classId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val date: String = "", // day of the lesson, "dd/MM/yyyy"
    val reason: String = "",
    val status: String = LeaveStatus.PENDING,
    val createdAt: Long = 0L
)

// Values of the `status` field in the `leaveRequests` collection
object LeaveStatus {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"

    fun label(status: String): String = when (status) {
        PENDING -> "Chờ duyệt"
        APPROVED -> "Đã duyệt"
        REJECTED -> "Từ chối"
        else -> "-"
    }
}
