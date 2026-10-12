package vn.edu.actvn.kstudy.data.model

// Values of the `status` field in the `records` collection
object AttendanceStatus {
    const val PRESENT = "PRESENT"
    const val LATE = "LATE"
    const val ABSENT = "ABSENT"

    // Absent with an approved leave request: not counted towards the exam-ban threshold
    const val EXCUSED = "EXCUSED"

    fun label(status: String): String = when (status) {
        PRESENT -> "Có mặt"
        LATE -> "Muộn"
        ABSENT -> "Vắng"
        EXCUSED -> "Có phép"
        else -> "-"
    }
}
