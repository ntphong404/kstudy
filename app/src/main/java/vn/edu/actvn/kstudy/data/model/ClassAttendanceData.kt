package vn.edu.actvn.kstudy.data.model

// Everything needed to compute statistics for one class: roster, sessions and their records
data class ClassAttendanceData(
    val courseClass: CourseClass = CourseClass(),
    val students: List<Student> = emptyList(),
    val sessions: List<AttendanceSession> = emptyList(),
    val records: List<AttendanceRecord> = emptyList()
)
