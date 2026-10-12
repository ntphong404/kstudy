package vn.edu.actvn.kstudy.testutil

import vn.edu.actvn.kstudy.data.ai.AiService
import vn.edu.actvn.kstudy.data.ai.AiTurn
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource

// In-memory stand-in for Firestore
class FakeAttendanceDataSource(
    var classes: List<ClassAttendanceData> = emptyList()
) : AttendanceDataSource {

    var failure: Exception? = null
    val calls = mutableListOf<String>()

    override suspend fun getClassAttendance(classId: String): ClassAttendanceData? {
        calls.add("class:$classId")
        failure?.let { throw it }
        return classes.firstOrNull { it.courseClass.id == classId }
    }

    override suspend fun getAttendanceForTeacher(teacherId: String): List<ClassAttendanceData> {
        calls.add("teacher:$teacherId")
        failure?.let { throw it }
        return classes.filter { it.courseClass.teacherId == teacherId }
    }

    override suspend fun getAttendanceForStudent(studentId: String): List<ClassAttendanceData> {
        calls.add("student:$studentId")
        failure?.let { throw it }
        return classes.filter { data -> data.students.any { it.id == studentId } }
    }

    override suspend fun getAllAttendance(): List<ClassAttendanceData> {
        calls.add("all")
        failure?.let { throw it }
        return classes
    }
}

// Records what the app would have sent to Gemini and answers from a script
class FakeAiService : AiService {

    data class Request(val systemInstruction: String, val history: List<AiTurn>, val message: String)

    val requests = mutableListOf<Request>()
    var failure: Exception? = null
    var reply: (Request) -> String = { "Trả lời cho: ${it.message}" }

    override suspend fun generate(systemInstruction: String, history: List<AiTurn>, message: String): String {
        val request = Request(systemInstruction, history.toList(), message)
        requests.add(request)
        failure?.let { throw it }
        return reply(request)
    }
}
