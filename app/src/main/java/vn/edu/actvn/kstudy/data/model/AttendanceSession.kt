package vn.edu.actvn.kstudy.data.model

data class AttendanceSession(
    val id: String = "",
    val classId: String = "",
    val date: String = "",
    val qrCode: String = "",
    val isActive: Boolean = true
)
