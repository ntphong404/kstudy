package vn.edu.actvn.kstudy.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Student
import vn.edu.actvn.kstudy.util.AttendanceScore

// Source of the raw attendance data behind statistics, reports and the AI assistant; all of it is
// read-only except setAbsencePenalty. It is an interface so the ViewModels can be unit tested
// with an in-memory fake.
interface AttendanceDataSource {
    suspend fun getClassAttendance(classId: String): ClassAttendanceData?
    suspend fun getAttendanceForTeacher(teacherId: String): List<ClassAttendanceData>
    suspend fun getAttendanceForStudent(studentId: String): List<ClassAttendanceData>
    suspend fun getAllAttendance(): List<ClassAttendanceData>

    // The classes alone, without their students and attendance: all a timetable needs
    suspend fun getClassesForTeacher(teacherId: String): List<CourseClass>
    suspend fun getClassesForStudent(studentId: String): List<CourseClass>

    // The one thing written here: how many points of the attendance score an absence costs in
    // this class. It belongs with the data it changes the reading of.
    suspend fun setAbsencePenalty(classId: String, penalty: Double)
}

// The name in the teacher's profile wins; the copy stored on the class is only a fallback
// for a teacher whose account was deleted or has no name.
internal fun currentTeacherName(storedOnClass: String, fromProfile: String?): String =
    if (fromProfile.isNullOrBlank()) storedOnClass else fromProfile

class AttendanceStatsRepository : AttendanceDataSource {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    override suspend fun getClassAttendance(classId: String): ClassAttendanceData? {
        val classDoc = await(firestore.collection("classes").document(classId).get())
        if (!classDoc.exists()) return null
        return loadAttendance(classDoc, HashMap())
    }

    override suspend fun getAttendanceForTeacher(teacherId: String): List<ClassAttendanceData> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
        )
        val teacherNames = HashMap<String, String>()
        return querySnapshot.documents.map { loadAttendance(it, teacherNames) }
    }

    override suspend fun getAttendanceForStudent(studentId: String): List<ClassAttendanceData> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereArrayContains("studentIds", studentId)
                .get()
        )
        val teacherNames = HashMap<String, String>()
        return querySnapshot.documents.map { loadAttendance(it, teacherNames) }
    }

    override suspend fun getAllAttendance(): List<ClassAttendanceData> {
        val querySnapshot = await(firestore.collection("classes").get())
        val teacherNames = HashMap<String, String>()
        return querySnapshot.documents.map { loadAttendance(it, teacherNames) }
    }

    override suspend fun getClassesForTeacher(teacherId: String): List<CourseClass> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereEqualTo("teacherId", teacherId)
                .get()
        )
        val teacherNames = HashMap<String, String>()
        return querySnapshot.documents.map { toCourseClass(it, teacherNames) }
    }

    override suspend fun getClassesForStudent(studentId: String): List<CourseClass> {
        val querySnapshot = await(
            firestore.collection("classes")
                .whereArrayContains("studentIds", studentId)
                .get()
        )
        val teacherNames = HashMap<String, String>()
        return querySnapshot.documents.map { toCourseClass(it, teacherNames) }
    }

    override suspend fun setAbsencePenalty(classId: String, penalty: Double) {
        await(firestore.collection("classes").document(classId).update("absencePenalty", penalty))
    }

    private suspend fun toCourseClass(classDoc: DocumentSnapshot, teacherNames: MutableMap<String, String>) =
        CourseClass(
            id = classDoc.id,
            name = classDoc.getString("name") ?: "",
            academicYear = classDoc.getString("academicYear") ?: "",
            teacherId = classDoc.getString("teacherId") ?: "",
            room = classDoc.getString("room") ?: "",
            schedule = classDoc.getString("schedule") ?: "",
            startDate = classDoc.getString("startDate") ?: "",
            endDate = classDoc.getString("endDate") ?: "",
            totalStudents = (classDoc.get("studentIds") as? List<*>)?.size ?: 0,
            teacherName = loadTeacherName(classDoc, teacherNames),
            // A class saved before the score existed has no such field and uses the default
            absencePenalty = classDoc.getDouble("absencePenalty") ?: AttendanceScore.DEFAULT_PENALTY
        )

    // teacherNames: names already looked up during this call, so a teacher with several classes is read once
    private suspend fun loadAttendance(
        classDoc: DocumentSnapshot,
        teacherNames: MutableMap<String, String>
    ): ClassAttendanceData {
        val studentIds = (classDoc.get("studentIds") as? List<*>)?.map { it.toString() } ?: emptyList()

        val students = mutableListOf<Student>()
        for (chunk in studentIds.chunked(WHERE_IN_LIMIT)) {
            val querySnapshot = await(
                firestore.collection("users")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
            )
            for (doc in querySnapshot.documents) {
                students.add(doc.toStudent())
            }
        }

        val sessionsSnapshot = await(
            firestore.collection("sessions")
                .whereEqualTo("classId", classDoc.id)
                .get()
        )
        val sessions = sessionsSnapshot.documents.map { doc ->
            AttendanceSession(
                id = doc.id,
                classId = doc.getString("classId") ?: "",
                date = doc.getString("date") ?: "",
                qrCode = doc.getString("qrCode") ?: "",
                isActive = doc.getBoolean("isActive") ?: false
            )
        }

        val records = mutableListOf<AttendanceRecord>()
        for (chunk in sessions.map { it.id }.chunked(WHERE_IN_LIMIT)) {
            val querySnapshot = await(
                firestore.collection("records")
                    .whereIn("sessionId", chunk)
                    .get()
            )
            for (doc in querySnapshot.documents) {
                records.add(
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
        }

        return ClassAttendanceData(toCourseClass(classDoc, teacherNames), students, sessions, records)
    }

    // The `teacherName` field of a class is a copy made when the class was saved. It is not updated
    // when the teacher is renamed later, so the name is read from the teacher's own profile.
    private suspend fun loadTeacherName(classDoc: DocumentSnapshot, cache: MutableMap<String, String>): String {
        val storedOnClass = classDoc.getString("teacherName") ?: ""
        val teacherId = classDoc.getString("teacherId") ?: ""
        if (teacherId.isEmpty()) return storedOnClass
        cache[teacherId]?.let { return it }

        val teacherDoc = await(firestore.collection("users").document(teacherId).get())
        val name = currentTeacherName(storedOnClass, teacherDoc.getString("fullName"))
        cache[teacherId] = name
        return name
    }

    private companion object {
        // Firestore accepts at most 30 values in a single `in` filter
        const val WHERE_IN_LIMIT = 30
    }
}
