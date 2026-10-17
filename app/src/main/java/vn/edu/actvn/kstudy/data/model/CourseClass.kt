package vn.edu.actvn.kstudy.data.model

data class CourseClass(
    val id: String = "",
    val name: String = "",
    val academicYear: String = "",
    val teacherId: String = "",
    val room: String = "",
    val schedule: String = "",
    // First and last day the class meets, "dd/MM/yyyy". A class saved before these existed has
    // them blank and meets every week of its schedule.
    val startDate: String = "",
    val endDate: String = "",
    val totalStudents: Int = 0,
    val teacherName: String = "",
    // Points of the attendance score lost for every absence without permission, see AttendanceScore
    val absencePenalty: Double = 1.0
)
