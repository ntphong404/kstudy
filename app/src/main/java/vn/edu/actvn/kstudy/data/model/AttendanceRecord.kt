package vn.edu.actvn.kstudy.data.model

data class AttendanceRecord(
    val id: String = "",
    val sessionId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val status: String = "", // "PRESENT", "LATE", "ABSENT", "EXCUSED"
    val timeMarked: String = "",
    val deviceId: String = "" // phone the student scanned with; empty when marked by the teacher
)
