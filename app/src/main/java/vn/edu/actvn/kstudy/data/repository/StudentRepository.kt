package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.*
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

    suspend fun markAttendanceViaCode(studentId: String, code: String): Pair<Boolean, String> {
        try {
            val querySession = await(
                firestore.collection("sessions")
                    .whereEqualTo("qrCode", code)
                    .whereEqualTo("isActive", true)
                    .limit(1)
                    .get()
            )
            if (querySession.isEmpty) {
                return Pair(false, "Mã điểm danh không hợp lệ hoặc đã hết hạn!")
            }
            val sessionDoc = querySession.documents[0]
            val sessionId = sessionDoc.id
            val classId = sessionDoc.getString("classId") ?: ""

            val classDoc = await(firestore.collection("classes").document(classId).get())
            val enrolledIds = (classDoc.get("studentIds") as? List<*>)?.map { it.toString() } ?: emptyList()
            if (!enrolledIds.contains(studentId)) {
                return Pair(false, "Bạn không có trong danh sách lớp!")
            }

            val queryRecord = await(
                firestore.collection("records")
                    .whereEqualTo("sessionId", sessionId)
                    .whereEqualTo("studentId", studentId)
                    .limit(1)
                    .get()
            )
            if (queryRecord.isEmpty) {
                return Pair(false, "Không tìm thấy thông tin điểm danh của bạn!")
            }
            val recordDoc = queryRecord.documents[0]
            val currentStatus = recordDoc.getString("status") ?: ""
            if (currentStatus == "PRESENT" || currentStatus == "LATE") {
                return Pair(false, "Bạn đã điểm danh lớp này rồi!")
            }

            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeStr = timeFormatter.format(Date())

            await(
                firestore.collection("records").document(recordDoc.id).update(
                    mapOf(
                        "status" to "PRESENT",
                        "timeMarked" to timeStr
                    )
                )
            )
            return Pair(true, "Điểm danh thành công lúc $timeStr!")
        } catch (e: Exception) {
            return Pair(false, "Lỗi kết nối Firebase: ${e.localizedMessage}")
        }
    }
}
