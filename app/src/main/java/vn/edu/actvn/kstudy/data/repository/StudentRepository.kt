package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.util.CheckInRules
import vn.edu.actvn.kstudy.util.RotatingCode
import java.text.SimpleDateFormat
import java.util.*

class StudentRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    suspend fun getStudentByUserId(userId: String): Student? {
        val doc = await(firestore.collection("users").document(userId).get())
        if (!doc.exists()) return null
        return Student(
            id = doc.id,
            userId = doc.id,
            name = doc.getString("fullName") ?: "",
            studentCode = doc.getString("studentCode") ?: "",
            className = doc.getString("className") ?: "",
            phone = doc.getString("phone") ?: "",
            avatarUrl = doc.getString("avatarUrl") ?: ""
        )
    }

    suspend fun getClassesForStudent(studentId: String): List<CourseClass> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereArrayContains("studentIds", studentId)
                .get()
        )
        val list = mutableListOf<CourseClass>()
        for (doc in querySnapshot.documents) {
            val studentIds = doc.get("studentIds") as? List<*>
            val total = studentIds?.size ?: 0
            list.add(
                CourseClass(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    academicYear = doc.getString("academicYear") ?: "",
                    teacherId = doc.getString("teacherId") ?: "",
                    room = doc.getString("room") ?: "",
                    schedule = doc.getString("schedule") ?: "",
                    totalStudents = total
                )
            )
        }
        return list
    }

    suspend fun getAttendanceRate(studentId: String, classId: String): Int {
        try {
            val sessionsSnapshot = await(
                firestore.collection("sessions")
                    .whereEqualTo("classId", classId)
                    .get()
            )
            if (sessionsSnapshot.isEmpty) return 100
            
            var total = 0
            var present = 0
            for (sessDoc in sessionsSnapshot.documents) {
                val recordsSnapshot = await(
                    firestore.collection("records")
                        .whereEqualTo("sessionId", sessDoc.id)
                        .whereEqualTo("studentId", studentId)
                        .get()
                )
                for (recDoc in recordsSnapshot.documents) {
                    total++
                    val status = recDoc.getString("status") ?: ""
                    if (status == "PRESENT" || status == "LATE") {
                        present++
                    }
                }
            }
            return if (total > 0) (present * 100) / total else 100
        } catch (e: Exception) {
            e.printStackTrace()
            return 100
        }
    }

    suspend fun getAttendanceHistoryForStudentInClass(studentId: String, classId: String): List<AttendanceRecord> {
        val list = mutableListOf<AttendanceRecord>()
        try {
            val sessionsSnapshot = await(
                firestore.collection("sessions")
                    .whereEqualTo("classId", classId)
                    .get()
            )
            for (sessDoc in sessionsSnapshot.documents) {
                val dateStr = sessDoc.getString("date") ?: ""
                val recordsSnapshot = await(
                    firestore.collection("records")
                        .whereEqualTo("sessionId", sessDoc.id)
                        .whereEqualTo("studentId", studentId)
                        .get()
                )
                for (recDoc in recordsSnapshot.documents) {
                    list.add(
                        AttendanceRecord(
                            id = recDoc.id,
                            sessionId = sessDoc.id,
                            studentId = studentId,
                            studentName = "",
                            studentCode = "",
                            status = recDoc.getString("status") ?: "ABSENT",
                            timeMarked = (recDoc.getString("timeMarked") ?: "-") + " ($dateStr)"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // scannedAt: when the code was scanned or typed. The code is checked against that moment,
    // so the seconds spent waiting for a GPS position cannot make a valid code expire.
    // locate: asks the phone for its position; only called when the session checks the location.
    suspend fun markAttendanceViaCode(
        studentId: String,
        code: String,
        scannedAt: Long,
        deviceId: String,
        locate: suspend () -> CheckInRules.Position?
    ): Pair<Boolean, String> {
        try {
            // The rotating code is not stored anywhere, so it is compared with what each open session shows now
            val activeSessions = await(
                firestore.collection("sessions")
                    .whereEqualTo("isActive", true)
                    .get()
            ).documents.map { it.toAttendanceSession() }
            val session = activeSessions.firstOrNull { RotatingCode.matches(it.secret, code, scannedAt) }
                ?: return Pair(false, "Mã điểm danh không đúng hoặc đã hết hạn! Hãy quét lại mã đang hiển thị.")

            val classDoc = await(firestore.collection("classes").document(session.classId).get())
            val enrolledIds = (classDoc.get("studentIds") as? List<*>)?.map { it.toString() } ?: emptyList()
            if (!enrolledIds.contains(studentId)) {
                return Pair(false, "Bạn không có trong danh sách lớp!")
            }

            val records = await(
                firestore.collection("records")
                    .whereEqualTo("sessionId", session.id)
                    .get()
            ).documents.map { it.toAttendanceRecord() }
            val position = if (session.location != null) locate() else null

            when (val result = CheckInRules.check(session, records, studentId, deviceId, position, scannedAt)) {
                is CheckInRules.Result.Rejected -> return Pair(false, result.message)
                is CheckInRules.Result.Accepted -> {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(scannedAt))
                    await(
                        firestore.collection("records").document(result.recordId).update(
                            mapOf(
                                "status" to result.status,
                                "timeMarked" to timeStr,
                                "deviceId" to deviceId
                            )
                        )
                    )
                    return if (result.status == AttendanceStatus.LATE) {
                        Pair(
                            true,
                            "Đã điểm danh lúc $timeStr, tính ĐI MUỘN vì quá ${session.lateAfterMinutes} phút kể từ khi mở phiên."
                        )
                    } else {
                        Pair(true, "Điểm danh thành công lúc $timeStr!")
                    }
                }
            }
        } catch (e: Exception) {
            return Pair(false, "Lỗi kết nối Firebase: ${e.localizedMessage}")
        }
    }
}
