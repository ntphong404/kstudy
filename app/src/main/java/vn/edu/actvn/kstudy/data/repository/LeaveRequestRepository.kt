package vn.edu.actvn.kstudy.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus
import vn.edu.actvn.kstudy.util.LeaveRequestRules

// Leave requests live in the `leaveRequests` collection. Unlike the older repositories these
// methods let Firestore errors through, so the ViewModels can tell the user what went wrong.
class LeaveRequestRepository {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    suspend fun getForStudent(studentId: String, classId: String): List<LeaveRequest> {
        val querySnapshot = await(
            firestore.collection(COLLECTION)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("classId", classId)
                .get()
        )
        return querySnapshot.documents.map { it.toLeaveRequest() }
    }

    suspend fun getForClass(classId: String): List<LeaveRequest> {
        val querySnapshot = await(
            firestore.collection(COLLECTION)
                .whereEqualTo("classId", classId)
                .get()
        )
        return querySnapshot.documents.map { it.toLeaveRequest() }
    }

    suspend fun submit(request: LeaveRequest) {
        await(
            firestore.collection(COLLECTION).add(
                mapOf(
                    "classId" to request.classId,
                    "studentId" to request.studentId,
                    "studentName" to request.studentName,
                    "studentCode" to request.studentCode,
                    "date" to request.date,
                    "reason" to request.reason,
                    "status" to request.status,
                    "createdAt" to request.createdAt
                )
            )
        )
    }

    // Students of the class whose absence on that day ("dd/MM/yyyy") has already been approved
    suspend fun excusedStudentIds(classId: String, date: String): Set<String> {
        val querySnapshot = await(
            firestore.collection(COLLECTION)
                .whereEqualTo("classId", classId)
                .whereEqualTo("date", date)
                .whereEqualTo("status", LeaveStatus.APPROVED)
                .get()
        )
        return querySnapshot.documents.mapNotNull { it.getString("studentId") }.toSet()
    }

    // The decision and its effect on the attendance records are saved in one batch: a request can
    // never stay "pending" while the reports already count the absence as excused, or the reverse.
    suspend fun decide(request: LeaveRequest, approve: Boolean) {
        val batch = firestore.batch()
        if (approve) {
            for ((record, excused) in absencesToExcuse(request)) {
                batch.update(record, "status", excused)
            }
        }
        batch.update(
            firestore.collection(COLLECTION).document(request.id),
            "status", if (approve) LeaveStatus.APPROVED else LeaveStatus.REJECTED
        )
        await(batch.commit())
    }

    // If the lesson has already been held, the student's absence in it becomes an excused one:
    // returns those records with their new status. A lesson that has not been opened yet is
    // handled when its session is created.
    private suspend fun absencesToExcuse(request: LeaveRequest): List<Pair<DocumentReference, String>> {
        val sessionsSnapshot = await(
            firestore.collection("sessions")
                .whereEqualTo("classId", request.classId)
                .get()
        )
        val sessionsThatDay = sessionsSnapshot.documents.filter { (it.getString("date") ?: "").startsWith(request.date) }
        val changes = mutableListOf<Pair<DocumentReference, String>>()
        for (sessionDoc in sessionsThatDay) {
            val recordsSnapshot = await(
                firestore.collection("records")
                    .whereEqualTo("sessionId", sessionDoc.id)
                    .whereEqualTo("studentId", request.studentId)
                    .get()
            )
            for (recordDoc in recordsSnapshot.documents) {
                val current = recordDoc.getString("status") ?: ""
                val excused = LeaveRequestRules.statusAfterApproval(current)
                if (excused != current) {
                    changes.add(recordDoc.reference to excused)
                }
            }
        }
        return changes
    }

    private companion object {
        const val COLLECTION = "leaveRequests"
    }
}
