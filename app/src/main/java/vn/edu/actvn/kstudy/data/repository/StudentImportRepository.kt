package vn.edu.actvn.kstudy.data.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.util.ExistingAccounts
import vn.edu.actvn.kstudy.util.ImportedStudent

// What importing a list of students needs from the database. It is an interface so the
// ViewModel can be unit tested with an in-memory fake.
interface StudentImportStore {
    suspend fun getExistingAccounts(): ExistingAccounts
    suspend fun getClasses(): List<CourseClass>

    // Creates the accounts and, when a class is given, adds them to it together with
    // alsoEnrollIds (students who already had an account). Everything is saved, or nothing is.
    suspend fun importStudents(students: List<ImportedStudent>, classId: String?, alsoEnrollIds: List<String>)
}

class StudentImportRepository : StudentImportStore {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    override suspend fun getExistingAccounts(): ExistingAccounts {
        val querySnapshot = await(firestore.collection("users").get())
        val usernames = HashSet<String>()
        val studentIdsByCode = HashMap<String, String>()
        for (doc in querySnapshot.documents) {
            doc.getString("username")?.let { usernames.add(it.trim().lowercase()) }
            val code = doc.getString("studentCode").orEmpty().trim()
            if (doc.getString("role") == Role.STUDENT.name && code.isNotEmpty()) {
                studentIdsByCode[code.lowercase()] = doc.id
            }
        }
        return ExistingAccounts(usernames, studentIdsByCode)
    }

    override suspend fun getClasses(): List<CourseClass> {
        val querySnapshot = await(firestore.collection("classes").get())
        return querySnapshot.documents.map { doc ->
            CourseClass(
                id = doc.id,
                name = doc.getString("name") ?: "",
                academicYear = doc.getString("academicYear") ?: "",
                totalStudents = (doc.get("studentIds") as? List<*>)?.size ?: 0
            )
        }
    }

    override suspend fun importStudents(students: List<ImportedStudent>, classId: String?, alsoEnrollIds: List<String>) {
        // BCrypt is slow on purpose, a tenth of a second for each password: spread over the cores
        val hashes = withContext(Dispatchers.Default) {
            students.map { async { org.mindrot.jbcrypt.BCrypt.hashpw(it.password, org.mindrot.jbcrypt.BCrypt.gensalt()) } }
                .awaitAll()
        }

        // StudentImport.MAX_STUDENTS keeps this within the 500 writes of one batch
        val batch = firestore.batch()
        val newIds = students.mapIndexed { index, entry ->
            val document = firestore.collection("users").document()
            batch.set(
                document,
                entry.student.toFields() + mapOf(
                    "username" to entry.username,
                    "passwordHash" to hashes[index],
                    // The admin has seen the first password: the student replaces it at the first sign-in
                    MUST_CHANGE_PASSWORD to true,
                    "role" to Role.STUDENT.name,
                    "avatarUrl" to "student_placeholder"
                )
            )
            document.id
        }
        val enrolled = newIds + alsoEnrollIds
        if (classId != null && enrolled.isNotEmpty()) {
            batch.update(
                firestore.collection("classes").document(classId),
                "studentIds", FieldValue.arrayUnion(*enrolled.toTypedArray())
            )
        }
        await(batch.commit())
    }
}
