package vn.edu.actvn.kstudy.data.repository

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.data.model.User
import java.util.UUID

// How an attempt to change one's own password ended
enum class PasswordChange { CHANGED, WRONG_CURRENT, FAILED }

class UserRepository(context: Context) {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    suspend fun login(username: String, pass: String): User? {
        initializeMockDataIfNeeded()

        val querySnapshot = await(
            firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
        )
        if (querySnapshot.isEmpty) return null
        val doc = querySnapshot.documents[0]
        val storedHash = doc.getString("passwordHash") ?: ""

        if (!passwordMatches(pass, storedHash)) return null
        return mapDocumentToUser(doc)
    }

    // An account written by hand in the Firebase console may still hold its password as plain text
    private fun passwordMatches(pass: String, storedHash: String): Boolean = try {
        org.mindrot.jbcrypt.BCrypt.checkpw(pass, storedHash)
    } catch (e: Exception) {
        pass == storedHash
    }

    // The current password is checked against the database again, so a phone left unlocked on
    // the profile screen is not enough to take over the account
    suspend fun changePassword(userId: String, currentPassword: String, newPassword: String): PasswordChange {
        return try {
            val doc = await(firestore.collection("users").document(userId).get())
            if (!doc.exists()) return PasswordChange.FAILED
            val storedHash = doc.getString("passwordHash") ?: ""
            // BCrypt is slow on purpose: keep it off the main thread
            val newHash = withContext(Dispatchers.Default) {
                if (passwordMatches(currentPassword, storedHash)) {
                    org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt())
                } else {
                    null
                }
            } ?: return PasswordChange.WRONG_CURRENT
            // A password the user chose: nobody else knows it any more
            await(doc.reference.update(mapOf("passwordHash" to newHash, MUST_CHANGE_PASSWORD to false)))
            PasswordChange.CHANGED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PasswordChange.FAILED
        }
    }

    suspend fun getUserProfile(userId: String): User? {
        val doc = await(firestore.collection("users").document(userId).get())
        if (!doc.exists()) return null
        return mapDocumentToUser(doc)
    }

    // What a user may change about themselves; the study and work details belong to the admin
    suspend fun updateUserProfile(userId: String, fullName: String, phone: String, personal: PersonalInfo): Boolean {
        return try {
            await(
                firestore.collection("users").document(userId).update(
                    mapOf(
                        "fullName" to fullName,
                        "phone" to phone
                    ) + personal.toFields()
                )
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun mapDocumentToUser(doc: DocumentSnapshot): User {
        val roleStr = doc.getString("role") ?: "STUDENT"
        return User(
            id = doc.id,
            username = doc.getString("username") ?: "",
            passwordHash = doc.getString("passwordHash") ?: "",
            fullName = doc.getString("fullName") ?: "",
            role = try { Role.valueOf(roleStr) } catch(e: Exception) { Role.STUDENT },
            phone = doc.getString("phone") ?: "",
            avatarUrl = doc.getString("avatarUrl") ?: "",
            mustChangePassword = doc.getBoolean(MUST_CHANGE_PASSWORD) ?: false,
            personal = doc.toPersonalInfo(),
            studentCode = doc.getString("studentCode") ?: "",
            className = doc.getString("className") ?: "",
            major = doc.getString("major") ?: "",
            subject = doc.getString("subject") ?: "",
            department = doc.getString("department") ?: "",
            degree = doc.getString("degree") ?: ""
        )
    }

    // Dedicated function to create/save a user to Firestore with BCrypt hashing
    suspend fun saveUserToFirebase(user: User, additionalFields: Map<String, Any> = emptyMap()): Boolean {
        return try {
            val hashedPassword = if (user.passwordHash.startsWith("$2a$") || user.passwordHash.startsWith("$2y$") || user.passwordHash.startsWith("$2b$")) {
                user.passwordHash
            } else {
                org.mindrot.jbcrypt.BCrypt.hashpw(user.passwordHash, org.mindrot.jbcrypt.BCrypt.gensalt())
            }
            val map = mutableMapOf<String, Any>(
                "username" to user.username,
                "passwordHash" to hashedPassword,
                "fullName" to user.fullName,
                "role" to user.role.name,
                "phone" to user.phone,
                "avatarUrl" to user.avatarUrl
            )
            map.putAll(user.personal.toFields())
            map.putAll(additionalFields)
            await(firestore.collection("users").document(user.id).set(map))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun initializeMockDataIfNeeded() {
        try {
            val usersSnapshot = await(firestore.collection("users").limit(1).get())
            if (usersSnapshot.isEmpty) {
                // Generate standard UUID strings for all mock records
                val adminId = UUID.randomUUID().toString()
                val teacher1Id = UUID.randomUUID().toString()
                val teacher2Id = UUID.randomUUID().toString()
                val teacher3Id = UUID.randomUUID().toString()

                val studentIds = (1..10).map { UUID.randomUUID().toString() }

                // 1 Admin
                val adminUser = User(adminId, "admin", "admin", "Admin User", Role.ADMIN, "0999999999")
                
                // 3 Teachers
                val teachers = listOf(
                    User(teacher1Id, "teacher1", "teacher1", "Giáo viên 1", Role.TEACHER, "0901111111"),
                    User(teacher2Id, "teacher2", "teacher2", "Giáo viên 2", Role.TEACHER, "0902222222"),
                    User(teacher3Id, "teacher3", "teacher3", "Giáo viên 3", Role.TEACHER, "0903333333")
                )

                // 10 Students
                val students = (1..10).map { i ->
                    User(studentIds[i - 1], "student$i", "student$i", "Học sinh $i", Role.STUDENT, "080${i}111111")
                }

                // Save Admin
                saveUserToFirebase(adminUser)

                // Save Teachers
                for (teacher in teachers) {
                    val subject = when (teacher.id) {
                        teacher1Id -> "Toán cao cấp"
                        teacher2Id -> "Vật lý lượng tử"
                        else -> "Tiếng Anh"
                    }
                    saveUserToFirebase(teacher, mapOf("subject" to subject))
                }

                // Save Students
                for (i in 0 until 10) {
                    val student = students[i]
                    val studentCode = String.format("SV%03d", i + 1)
                    saveUserToFirebase(
                        student,
                        mapOf(
                            "studentCode" to studentCode,
                            "className" to "K18"
                        )
                    )
                }

                // 2 Classes
                val class1Id = UUID.randomUUID().toString()
                val class2Id = UUID.randomUUID().toString()

                // Class 1: students 1 to 6
                val class1Students = studentIds.subList(0, 6)
                // Class 2: students 4 to 10
                val class2Students = studentIds.subList(3, 10)

                val classes = listOf(
                    mapOf(
                        "name" to "Toán cao cấp A1",
                        "academicYear" to "2025-2026",
                        "teacherId" to teacher1Id,
                        "teacherName" to "Giáo viên 1",
                        "room" to "Phòng 101",
                        "schedule" to "Thứ 2 (07:30-09:00)",
                        "studentIds" to class1Students
                    ),
                    mapOf(
                        "name" to "Vật lý lượng tử B2",
                        "academicYear" to "2025-2026",
                        "teacherId" to teacher2Id,
                        "teacherName" to "Giáo viên 2",
                        "room" to "Phòng 102",
                        "schedule" to "Thứ 3 (09:30-11:00)",
                        "studentIds" to class2Students
                    )
                )
                
                await(firestore.collection("classes").document(class1Id).set(classes[0]))
                await(firestore.collection("classes").document(class2Id).set(classes[1]))

                // Seed historical sessions
                val sess1Id = UUID.randomUUID().toString()
                val sess2Id = UUID.randomUUID().toString()

                val sessions = listOf(
                    mapOf(
                        "classId" to class1Id,
                        "date" to "01/07/2026",
                        "qrCode" to "A111",
                        "isActive" to false
                    ),
                    mapOf(
                        "classId" to class2Id,
                        "date" to "02/07/2026",
                        "qrCode" to "B222",
                        "isActive" to false
                    )
                )
                await(firestore.collection("sessions").document(sess1Id).set(sessions[0]))
                await(firestore.collection("sessions").document(sess2Id).set(sessions[1]))

                // Seed records for session 1 (class 1: student1 to student6)
                val recordsSess1 = (1..6).map { i ->
                    mapOf(
                        "sessionId" to sess1Id,
                        "studentId" to studentIds[i - 1],
                        "studentName" to "Học sinh $i",
                        "studentCode" to String.format("SV%03d", i),
                        "status" to if (i % 2 == 0) "PRESENT" else "LATE",
                        "timeMarked" to "07:35"
                    )
                }
                
                // Seed records for session 2 (class 2: student4 to student10)
                val recordsSess2 = (4..10).map { i ->
                    mapOf(
                        "sessionId" to sess2Id,
                        "studentId" to studentIds[i - 1],
                        "studentName" to "Học sinh $i",
                        "studentCode" to String.format("SV%03d", i),
                        "status" to if (i % 3 == 0) "PRESENT" else "ABSENT",
                        "timeMarked" to if (i % 3 == 0) "09:42" else "-"
                    )
                }

                for (rec in recordsSess1) {
                    val recId = UUID.randomUUID().toString()
                    await(firestore.collection("records").document(recId).set(rec))
                }
                for (rec in recordsSess2) {
                    val recId = UUID.randomUUID().toString()
                    await(firestore.collection("records").document(recId).set(rec))
                }
                android.util.Log.d("Kstudy", "Mock data successfully seeded to Firebase Firestore!")
            } else {
                android.util.Log.d("Kstudy", "Firestore is already seeded. Skipping initial seed.")
            }
        } catch (e: Exception) {
            android.util.Log.e("Kstudy", "Error seeding mock data to Firestore: ${e.message}", e)
        }
    }
}
