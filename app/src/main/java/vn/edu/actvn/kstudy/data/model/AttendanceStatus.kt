package vn.edu.actvn.kstudy.data.model

// Values of the `status` field in the `records` collection
object AttendanceStatus {
    const val PRESENT = "PRESENT"
    const val LATE = "LATE"
    const val ABSENT = "ABSENT"

    fun label(status: String): String = when (status) {
        PRESENT -> "Có mặt"
        LATE -> "Muộn"
        ABSENT -> "Vắng"
        else -> "-"
    }
}
