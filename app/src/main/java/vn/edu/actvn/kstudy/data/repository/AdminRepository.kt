package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.*

class AdminRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    // --- TEACHER CRUD ---

    suspend fun getTeachers(): List<Teacher> {
        val querySnapshot = await(
            firestore.collection("users")
                .whereEqualTo("role", Role.TEACHER.name)
                .get()
        )
        val list = mutableListOf<Teacher>()
        for (doc in querySnapshot.documents) {
            list.add(doc.toTeacher())
        }
        return list
    }

    suspend fun addTeacher(username: String, pass: String, teacher: Teacher): Boolean {
        return try {
            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(pass, org.mindrot.jbcrypt.BCrypt.gensalt())
            val data = teacher.toFields() + mapOf(
                "username" to username,
                "passwordHash" to hashedPassword,
                "role" to Role.TEACHER.name,
                "avatarUrl" to "teacher_placeholder"
            )
            await(firestore.collection("users").add(data))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateTeacher(teacher: Teacher): Boolean {
        return try {
            await(firestore.collection("users").document(teacher.userId).update(teacher.toFields()))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteTeacher(userId: String): Boolean {
        return try {
            await(firestore.collection("users").document(userId).delete())
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- STUDENT CRUD ---

    suspend fun getStudents(): List<Student> {
        val querySnapshot = await(
            firestore.collection("users")
                .whereEqualTo("role", Role.STUDENT.name)
                .get()
        )
        val list = mutableListOf<Student>()
        for (doc in querySnapshot.documents) {
            list.add(doc.toStudent())
        }
        return list
    }

    suspend fun addStudent(username: String, pass: String, student: Student): Boolean {
        return try {
            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(pass, org.mindrot.jbcrypt.BCrypt.gensalt())
            val data = student.toFields() + mapOf(
                "username" to username,
                "passwordHash" to hashedPassword,
                "role" to Role.STUDENT.name,
                "avatarUrl" to "student_placeholder"
            )
            await(firestore.collection("users").add(data))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateStudent(student: Student): Boolean {
        return try {
            await(firestore.collection("users").document(student.userId).update(student.toFields()))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteStudent(userId: String): Boolean {
        return try {
            await(firestore.collection("users").document(userId).delete())
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- CLASS CRUD ---

    suspend fun getClasses(): List<CourseClass> {
        val querySnapshot = await(firestore.collection("classes").get())
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

    suspend fun addClass(name: String, academicYear: String, teacherId: String, room: String, schedule: String): Boolean {
        return try {
            // Get teacher name
            val teacherDoc = await(firestore.collection("users").document(teacherId).get())
            val teacherName = teacherDoc.getString("fullName") ?: "Giáo viên"
            val data = mapOf(
                "name" to name,
                "academicYear" to academicYear,
                "teacherId" to teacherId,
                "teacherName" to teacherName,
                "room" to room,
                "schedule" to schedule,
                "studentIds" to emptyList<String>()
            )
            await(firestore.collection("classes").add(data))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun updateClass(classId: String, name: String, academicYear: String, teacherId: String, room: String, schedule: String): Boolean {
        return try {
            val teacherDoc = await(firestore.collection("users").document(teacherId).get())
            val teacherName = teacherDoc.getString("fullName") ?: "Giáo viên"
            await(
                firestore.collection("classes").document(classId).update(
                    mapOf(
                        "name" to name,
                        "academicYear" to academicYear,
                        "teacherId" to teacherId,
                        "teacherName" to teacherName,
                        "room" to room,
                        "schedule" to schedule
                    )
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteClass(classId: String): Boolean {
        return try {
            await(firestore.collection("classes").document(classId).delete())
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- ENROLLMENTS ---

    suspend fun getStudentsInClass(classId: String): List<Student> {
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

    suspend fun getStudentsNotInClass(classId: String): List<Student> {
        val classDoc = await(firestore.collection("classes").document(classId).get())
        val enrolledIds = (classDoc.get("studentIds") as? List<*>)?.map { it.toString() } ?: emptyList()
        
        val querySnapshot = await(
            firestore.collection("users")
                .whereEqualTo("role", Role.STUDENT.name)
                .get()
        )
        val list = mutableListOf<Student>()
        for (doc in querySnapshot.documents) {
            if (!enrolledIds.contains(doc.id)) {
                list.add(doc.toStudent())
            }
        }
        return list
    }

    suspend fun enrollStudent(classId: String, studentId: String): Boolean {
        return try {
            await(
                firestore.collection("classes").document(classId).update(
                    "studentIds", FieldValue.arrayUnion(studentId)
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun unenrollStudent(classId: String, studentId: String): Boolean {
        return try {
            await(
                firestore.collection("classes").document(classId).update(
                    "studentIds", FieldValue.arrayRemove(studentId)
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- SYSTEM STATS ---

    suspend fun getSystemStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        try {
            // Count students
            val studentsSnapshot = await(
                firestore.collection("users")
                    .whereEqualTo("role", Role.STUDENT.name)
                    .get()
            )
            stats["students"] = studentsSnapshot.size()

            // Count teachers
            val teachersSnapshot = await(
                firestore.collection("users")
                    .whereEqualTo("role", Role.TEACHER.name)
                    .get()
            )
            stats["teachers"] = teachersSnapshot.size()

            // Count classes
            val classesSnapshot = await(firestore.collection("classes").get())
            stats["classes"] = classesSnapshot.size()

            // The attendance rate of the dashboard comes from StatsViewModel.loadSystemOverview()
        } catch (e: Exception) {
            e.printStackTrace()
            stats["students"] = 0
            stats["teachers"] = 0
            stats["classes"] = 0
        }
        return stats
    }

    // Real-time overview of classes currently taking attendance, for the dashboard
    suspend fun getActiveSessionsOverview(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val sessionsSnapshot = await(
                firestore.collection("sessions").whereEqualTo("isActive", true).get()
            )
            for (doc in sessionsSnapshot.documents) {
                val classId = doc.getString("classId") ?: continue
                val classDoc = await(firestore.collection("classes").document(classId).get())
                val className = classDoc.getString("name") ?: "Lớp học"
                val date = doc.getString("date") ?: ""
                list.add(Pair(className, date))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
