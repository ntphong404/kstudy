package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.*
import java.text.SimpleDateFormat
import java.util.*

class TeacherRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    suspend fun getTeacherByUserId(userId: String): Teacher? {
        val doc = await(firestore.collection("users").document(userId).get())
        if (!doc.exists()) return null
        return Teacher(
            id = doc.id,
            userId = doc.id,
            name = doc.getString("fullName") ?: "",
            subject = doc.getString("subject") ?: "",
            phone = doc.getString("phone") ?: "",
            avatarUrl = doc.getString("avatarUrl") ?: ""
        )
    }

    suspend fun getClassesForTeacher(teacherId: String): List<CourseClass> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereEqualTo("teacherId", teacherId)
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

    suspend fun getStudentsForClass(classId: String): List<Student> {
        val classDoc = await(firestore.collection("classes").document(classId).get())
        val studentIds = classDoc.get("studentIds") as? List<*> ?: return emptyList()
        val list = mutableListOf<Student>()
        for (sid in studentIds) {
            val studentIdStr = sid.toString()
            val doc = await(firestore.collection("users").document(studentIdStr).get())
            if (doc.exists()) {
                list.add(
                    Student(
                        id = doc.id,
                        userId = doc.id,
                        name = doc.getString("fullName") ?: "",
                        studentCode = doc.getString("studentCode") ?: "",
                        className = doc.getString("className") ?: "",
                        phone = doc.getString("phone") ?: "",
                        avatarUrl = doc.getString("avatarUrl") ?: ""
                    )
                )
            }
        }
        return list
    }

    suspend fun createAttendanceSession(classId: String, qrCode: String): String {
        try {
            val todayDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            // A class only meets once per calendar day, so a session already opened today
            // for this class IS today's session — reopen it instead of creating a duplicate.
            val allSessionsSnapshot = await(
                firestore.collection("sessions")
                    .whereEqualTo("classId", classId)
                    .get()
            )
            val todaySessionDoc = allSessionsSnapshot.documents.firstOrNull { doc ->
                (doc.getString("date") ?: "").startsWith(todayDateOnly)
            }

            // Close any active session for this class that isn't today's (e.g. left open from a previous day)
            for (doc in allSessionsSnapshot.documents) {
                val isActive = doc.getBoolean("isActive") ?: false
                if (isActive && doc.id != todaySessionDoc?.id) {
                    await(firestore.collection("sessions").document(doc.id).update("isActive", false))
                }
            }

            if (todaySessionDoc != null) {
                // Reopen today's session with a fresh QR code, keep its original records untouched
                await(
                    firestore.collection("sessions").document(todaySessionDoc.id).update(
                        mapOf("isActive" to true, "qrCode" to qrCode)
                    )
                )
                return todaySessionDoc.id
            }

            // No session yet today: create a brand new one
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateStr = formatter.format(Date())

            val sessionData = mapOf(
                "classId" to classId,
                "date" to dateStr,
                "qrCode" to qrCode,
                "isActive" to true
            )
            val sessionRef = await(firestore.collection("sessions").add(sessionData))
            val sessionId = sessionRef.id

            // Pre-populate records as ABSENT for all students in the class
            val students = getStudentsForClass(classId)
            for (student in students) {
                val recordData = mapOf(
                    "sessionId" to sessionId,
                    "studentId" to student.id,
                    "studentName" to student.name,
                    "studentCode" to student.studentCode,
                    "status" to "ABSENT",
                    "timeMarked" to "-"
                )
                await(firestore.collection("records").add(recordData))
            }

            return sessionId
        } catch (e: Exception) {
            return ""
        }
    }

    suspend fun closeAttendanceSession(sessionId: String) {
        try {
            await(firestore.collection("sessions").document(sessionId).update("isActive", false))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getRecordsForSession(sessionId: String): List<AttendanceRecord> {
        val querySnapshot = await(
            firestore.collection("records")
                .whereEqualTo("sessionId", sessionId)
                .get()
        )
        val list = mutableListOf<AttendanceRecord>()
        for (doc in querySnapshot.documents) {
            list.add(
                AttendanceRecord(
                    id = doc.id,
                    sessionId = doc.getString("sessionId") ?: "",
                    studentId = doc.getString("studentId") ?: "",
                    studentName = doc.getString("studentName") ?: "",
                    studentCode = doc.getString("studentCode") ?: "",
                    status = doc.getString("status") ?: "",
                    timeMarked = doc.getString("timeMarked") ?: ""
                )
            )
        }
        // Sort alphabetically by student name
        list.sortBy { it.studentName }
        return list
    }

    suspend fun updateRecordStatus(recordId: String, status: String, timeMarked: String): Boolean {
        return try {
            await(
                firestore.collection("records").document(recordId).update(
                    mapOf(
                        "status" to status,
                        "timeMarked" to timeMarked
                    )
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getActiveSession(classId: String): AttendanceSession? {
        val querySnapshot = await(
            firestore.collection("sessions")
                .whereEqualTo("classId", classId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
        )
        if (querySnapshot.isEmpty) return null
        val doc = querySnapshot.documents[0]
        return AttendanceSession(
            id = doc.id,
            classId = doc.getString("classId") ?: "",
            date = doc.getString("date") ?: "",
            qrCode = doc.getString("qrCode") ?: "",
            isActive = true
        )
    }

    suspend fun getPastSessionsForClass(classId: String): List<AttendanceSession> {
        val querySnapshot = await(
            firestore.collection("sessions")
                .whereEqualTo("classId", classId)
                .whereEqualTo("isActive", false)
                .get()
        )
        val list = mutableListOf<AttendanceSession>()
        for (doc in querySnapshot.documents) {
            list.add(
                AttendanceSession(
                    id = doc.id,
                    classId = doc.getString("classId") ?: "",
                    date = doc.getString("date") ?: "",
                    qrCode = doc.getString("qrCode") ?: "",
                    isActive = false
                )
            )
        }
        return list
    }
}
