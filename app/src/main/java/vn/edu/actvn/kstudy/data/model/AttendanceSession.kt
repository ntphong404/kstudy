package vn.edu.actvn.kstudy.data.model

data class AttendanceSession(
    val id: String = "",
    val classId: String = "",
    val date: String = "",
    val qrCode: String = "", // static code of sessions opened before the rotating QR code existed
    val isActive: Boolean = true,
    val secret: String = "", // key the rotating QR code is derived from
    val openedAt: Long = 0L, // epoch millis; 0 when unknown (older sessions)
    val lateAfterMinutes: Int = 0, // a scan later than this after opening is LATE; 0 = never late
    val location: GeoLocation? = null, // where the teacher opened the session; null = no location check
    val locationAccuracyMeters: Float = 0f // how uncertain the teacher's phone was about `location`
)

data class GeoLocation(
    val latitude: Double,
    val longitude: Double
)
