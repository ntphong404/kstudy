package vn.edu.actvn.kstudy.data.model

data class AttendanceRecord(
    val id: String = "",
    val sessionId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val status: String = "", // "PRESENT", "LATE", "ABSENT"
    val timeMarked: String = ""
)
