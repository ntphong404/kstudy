package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import vn.edu.actvn.kstudy.data.model.GeoLocation
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Decides whether a student's scan is accepted and with which status.
// Kept free of Firestore and Android classes so every rule can be unit tested.
object CheckInRules {

    // How far from the teacher's phone a student may be
    const val MAX_DISTANCE_METERS = 100

    // Inside a building a position is easily off by tens of metres, and each phone reports how
    // uncertain its own position is. That uncertainty (teacher's plus student's) is given to the
    // student as extra distance, but never more than this: a very vague position must not let
    // in somebody who is far away.
    const val MAX_ACCURACY_ALLOWANCE_METERS = 100

    // Choices offered to the teacher when opening a session
    val LATE_OPTIONS_MINUTES = listOf(1, 5, 10, 15)
    const val DEFAULT_LATE_AFTER_MINUTES = 10

    sealed class Result {
        data class Accepted(val recordId: String, val status: String) : Result()
        data class Rejected(val message: String) : Result()
    }

    // Where a phone is right now. accuracyMeters is the radius the phone itself reports for its
    // position (0 when unknown); isMock is true when the position comes from a fake GPS app.
    data class Position(
        val location: GeoLocation,
        val isMock: Boolean = false,
        val accuracyMeters: Float = 0f
    )

    fun check(
        session: AttendanceSession,
        records: List<AttendanceRecord>, // every record of this session
        studentId: String,
        deviceId: String,
        position: Position?, // null when the phone could not provide one
        nowMillis: Long
    ): Result {
        val own = records.firstOrNull { it.studentId == studentId }
            ?: return Result.Rejected("Không tìm thấy thông tin điểm danh của bạn!")
        if (own.status == AttendanceStatus.PRESENT || own.status == AttendanceStatus.LATE) {
            return Result.Rejected("Bạn đã điểm danh lớp này rồi!")
        }

        // One phone, one account per session: stops a student from logging in as an absent friend
        if (deviceId.isNotEmpty() && records.any { it.deviceId == deviceId && it.studentId != studentId }) {
            return Result.Rejected("Thiết bị này đã được dùng để điểm danh cho tài khoản khác trong buổi học này!")
        }

        val classroom = session.location
        if (classroom != null) {
            if (position == null) {
                return Result.Rejected("Không xác định được vị trí của bạn. Hãy bật định vị (GPS) và cấp quyền vị trí cho ứng dụng!")
            }
            if (position.isMock) {
                return Result.Rejected("Phát hiện vị trí giả lập. Hãy tắt ứng dụng giả lập vị trí rồi thử lại!")
            }
            val distance = distanceMeters(classroom, position.location)
            val allowance = min(
                session.locationAccuracyMeters + position.accuracyMeters,
                MAX_ACCURACY_ALLOWANCE_METERS.toFloat()
            )
            if (distance - allowance > MAX_DISTANCE_METERS) {
                return Result.Rejected(
                    "Bạn đang cách lớp học khoảng ${distance.roundToInt()} m, " +
                        "ngoài phạm vi $MAX_DISTANCE_METERS m cho phép!"
                )
            }
        }

        return Result.Accepted(own.id, statusAt(session, nowMillis))
    }

    // PRESENT, or LATE when the scan comes more than `lateAfterMinutes` after the session was opened
    fun statusAt(session: AttendanceSession, nowMillis: Long): String {
        // Sessions opened by an older version of the app only have the `date` string
        val openedAt = if (session.openedAt > 0) session.openedAt else SessionDates.toMillis(session.date)
        if (session.lateAfterMinutes <= 0 || openedAt <= 0) return AttendanceStatus.PRESENT
        val lateAfterMillis = session.lateAfterMinutes * 60_000L
        return if (nowMillis - openedAt > lateAfterMillis) AttendanceStatus.LATE else AttendanceStatus.PRESENT
    }

    // Great-circle distance (haversine formula)
    fun distanceMeters(a: GeoLocation, b: GeoLocation): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
        return 2 * earthRadiusMeters * asin(sqrt(h))
    }
}
