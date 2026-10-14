package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.util.CheckInRules
import vn.edu.actvn.kstudy.util.LeaveRequestRules
import vn.edu.actvn.kstudy.util.RotatingCode
import java.text.SimpleDateFormat
import java.util.*

class TeacherRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val leaveRequests = LeaveRequestRepository()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    suspend fun getTeacherByUserId(userId: String): Teacher? {
        val doc = await(firestore.collection("users").document(userId).get())
        if (!doc.exists()) return null
        return doc.toTeacher()
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
                list.add(doc.toStudent())
            }
        }
        return list
    }

    // The class and the ids of its students: who to notify about it, and under which name
    suspend fun getClassWithStudentIds(classId: String): Pair<CourseClass, List<String>>? {
        val doc = await(firestore.collection("classes").document(classId).get())
        if (!doc.exists()) return null
        val studentIds = (doc.get("studentIds") as? List<*>)?.map { it.toString() } ?: emptyList()
        val courseClass = CourseClass(
            id = doc.id,
            name = doc.getString("name") ?: "",
            academicYear = doc.getString("academicYear") ?: "",
            teacherId = doc.getString("teacherId") ?: "",
            room = doc.getString("room") ?: "",
            schedule = doc.getString("schedule") ?: "",
            totalStudents = studentIds.size
        )
        return courseClass to studentIds
    }

    // position: where the teacher is standing, or null to open the session without a location check
    suspend fun createAttendanceSession(classId: String, lateAfterMinutes: Int, position: CheckInRules.Position?): String {
        try {
            val now = Date()
            val todayDateOnly = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)

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

            // Every opening gets a new key for the rotating QR code and the teacher's current position
            val opening = mapOf(
                "isActive" to true,
                "secret" to RotatingCode.newSecret(),
                "lateAfterMinutes" to lateAfterMinutes,
                "latitude" to position?.location?.latitude,
                "longitude" to position?.location?.longitude,
                "locationAccuracy" to position?.accuracyMeters
            )

            if (todaySessionDoc != null) {
                // Reopen today's session, keep its original records and opening time untouched
                await(firestore.collection("sessions").document(todaySessionDoc.id).update(opening))
                return todaySessionDoc.id
            }

            // No session yet today: create a brand new one. Everything it needs is read first, then
            // the session and its records are written in a single batch (at most 500 writes, so up
            // to 499 students). Either all of it is saved or nothing: a network failure cannot
            // leave a session with missing records that every later attempt would just reopen.
            val excusedStudentIds = leaveRequests.excusedStudentIds(classId, todayDateOnly)
            val students = getStudentsForClass(classId)
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            val sessionRef = firestore.collection("sessions").document()
            val batch = firestore.batch()
            batch.set(
                sessionRef,
                opening + mapOf(
                    "classId" to classId,
                    "date" to formatter.format(now),
                    "openedAt" to now.time
                )
            )
            // One record per student in the class: ABSENT until they scan,
            // or EXCUSED when their leave request for today has been approved
            for (student in students) {
                batch.set(
                    firestore.collection("records").document(),
                    mapOf(
                        "sessionId" to sessionRef.id,
                        "studentId" to student.id,
                        "studentName" to student.name,
                        "studentCode" to student.studentCode,
                        "status" to LeaveRequestRules.initialStatus(student.id, excusedStudentIds),
                        "timeMarked" to "-"
                    )
                )
            }
            await(batch.commit())

            return sessionRef.id
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
        return querySnapshot.documents[0].toAttendanceSession()
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
