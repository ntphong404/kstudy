package vn.edu.actvn.kstudy.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.GeoLocation
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus
import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.data.model.Student
import vn.edu.actvn.kstudy.data.model.Teacher

// Students and teachers are documents of the `users` collection; a document written before a
// field existed simply reads as an empty string

internal fun DocumentSnapshot.toPersonalInfo(): PersonalInfo = PersonalInfo(
    email = getString("email") ?: "",
    dateOfBirth = getString("dateOfBirth") ?: "",
    gender = getString("gender") ?: "",
    address = getString("address") ?: ""
)

internal fun PersonalInfo.toFields(): Map<String, Any> = mapOf(
    "email" to email,
    "dateOfBirth" to dateOfBirth,
    "gender" to gender,
    "address" to address
)

internal fun DocumentSnapshot.toStudent(): Student = Student(
    id = id,
    userId = id,
    name = getString("fullName") ?: "",
    studentCode = getString("studentCode") ?: "",
    className = getString("className") ?: "",
    phone = getString("phone") ?: "",
    avatarUrl = getString("avatarUrl") ?: "",
    major = getString("major") ?: "",
    personal = toPersonalInfo()
)

// Everything the admin's form can change about a student
internal fun Student.toFields(): Map<String, Any> = mapOf(
    "fullName" to name,
    "studentCode" to studentCode,
    "className" to className,
    "major" to major,
    "phone" to phone
) + personal.toFields()

internal fun DocumentSnapshot.toTeacher(): Teacher = Teacher(
    id = id,
    userId = id,
    name = getString("fullName") ?: "",
    subject = getString("subject") ?: "",
    phone = getString("phone") ?: "",
    avatarUrl = getString("avatarUrl") ?: "",
    department = getString("department") ?: "",
    degree = getString("degree") ?: "",
    personal = toPersonalInfo()
)

// Everything the admin's form can change about a teacher
internal fun Teacher.toFields(): Map<String, Any> = mapOf(
    "fullName" to name,
    "subject" to subject,
    "department" to department,
    "degree" to degree,
    "phone" to phone
) + personal.toFields()

internal fun DocumentSnapshot.toAttendanceSession(): AttendanceSession {
    val qrCode = getString("qrCode") ?: ""
    val latitude = getDouble("latitude")
    val longitude = getDouble("longitude")
    return AttendanceSession(
        id = id,
        classId = getString("classId") ?: "",
        date = getString("date") ?: "",
        qrCode = qrCode,
        isActive = getBoolean("isActive") ?: false,
        // A session left open by an older version of the app only has the static code. Using it
        // as the key lets that session be finished with a rotating code instead of breaking it.
        secret = getString("secret").orEmpty().ifEmpty { qrCode },
        openedAt = getLong("openedAt") ?: 0L,
        lateAfterMinutes = getLong("lateAfterMinutes")?.toInt() ?: 0,
        location = if (latitude != null && longitude != null) GeoLocation(latitude, longitude) else null,
        locationAccuracyMeters = getDouble("locationAccuracy")?.toFloat() ?: 0f
    )
}

internal fun DocumentSnapshot.toAttendanceRecord(): AttendanceRecord = AttendanceRecord(
    id = id,
    sessionId = getString("sessionId") ?: "",
    studentId = getString("studentId") ?: "",
    studentName = getString("studentName") ?: "",
    studentCode = getString("studentCode") ?: "",
    status = getString("status") ?: "",
    timeMarked = getString("timeMarked") ?: "",
    deviceId = getString("deviceId") ?: ""
)

internal fun DocumentSnapshot.toLeaveRequest(): LeaveRequest = LeaveRequest(
    id = id,
    classId = getString("classId") ?: "",
    studentId = getString("studentId") ?: "",
    studentName = getString("studentName") ?: "",
    studentCode = getString("studentCode") ?: "",
    date = getString("date") ?: "",
    reason = getString("reason") ?: "",
    status = getString("status") ?: LeaveStatus.PENDING,
    createdAt = getLong("createdAt") ?: 0L
)
