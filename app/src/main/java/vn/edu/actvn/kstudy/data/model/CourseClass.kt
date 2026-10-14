package vn.edu.actvn.kstudy.data.model

data class CourseClass(
    val id: String = "",
    val name: String = "",
    val academicYear: String = "",
    val teacherId: String = "",
    val room: String = "",
    val schedule: String = "",
    val totalStudents: Int = 0,
    val teacherName: String = "",
    // Points of the attendance score lost for every absence without permission, see AttendanceScore
    val absencePenalty: Double = 1.0
)
