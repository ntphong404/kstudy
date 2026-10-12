package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Student
import kotlin.math.roundToInt

data class StatusCount(
    val present: Int = 0,
    val late: Int = 0,
    val absent: Int = 0, // absent without permission: the only kind that counts towards the exam ban
    val excused: Int = 0 // absent with an approved leave request
) {
    val total: Int get() = present + late + absent + excused

    // Being late still counts as having attended the session
    val attended: Int get() = present + late

    // Percent of sessions attended; null while there is no data, so the UI can show "--" instead of a made-up number
    val attendanceRate: Int? get() = if (total > 0) (attended * 100.0 / total).roundToInt() else null

    // Percent of sessions missed without permission. Without excused absences it is the complement
    // of attendanceRate, so the two always sum to 100 despite rounding.
    val absenceRate: Int?
        get() = when {
            total == 0 -> null
            excused == 0 -> attendanceRate?.let { 100 - it }
            else -> (absent * 100.0 / total).roundToInt()
        }

    operator fun plus(other: StatusCount) = StatusCount(
        present + other.present,
        late + other.late,
        absent + other.absent,
        excused + other.excused
    )

    companion object {
        fun of(statuses: Collection<String>) = StatusCount(
            present = statuses.count { it == AttendanceStatus.PRESENT },
            late = statuses.count { it == AttendanceStatus.LATE },
            absent = statuses.count { it == AttendanceStatus.ABSENT },
            excused = statuses.count { it == AttendanceStatus.EXCUSED }
        )
    }
}

enum class RiskLevel { SAFE, WARNING, DANGER }

data class StudentStat(
    val student: Student,
    val count: StatusCount,
    val risk: RiskLevel
)

data class SessionStat(
    val session: AttendanceSession,
    val count: StatusCount
)

data class ClassReport(
    val courseClass: CourseClass,
    val sessions: List<SessionStat>, // closed sessions, oldest first
    val students: List<StudentStat>, // current roster, by student code
    private val statuses: Map<String, Map<String, String>> // studentId -> sessionId -> status
) {
    val overall: StatusCount
        get() = students.fold(StatusCount()) { acc, stat -> acc + stat.count }

    // Most absent first
    val atRisk: List<StudentStat>
        get() = students
            .filter { it.risk != RiskLevel.SAFE }
            .sortedWith(compareByDescending<StudentStat> { it.risk }.thenByDescending { it.count.absent })

    fun statusOf(studentId: String, sessionId: String): String? = statuses[studentId]?.get(sessionId)
}

// One student's attendance in one class
data class StudentClassStat(
    val courseClass: CourseClass,
    val count: StatusCount,
    val risk: RiskLevel,
    val history: List<Pair<AttendanceSession, String>> // closed sessions, oldest first, with this student's status
)

// Statistics only use sessions that have been closed: while a session is still open every
// student who has not scanned yet is stored as ABSENT, which would raise false warnings.
object AttendanceAnalyzer {

    // Absent without permission in more than 20% of the sessions held so far => at risk of being
    // barred from the exam. Excused absences are sessions held, but never push a student over.
    const val BAN_THRESHOLD_PERCENT = 20
    const val WARNING_THRESHOLD_PERCENT = 10

    fun riskOf(count: StatusCount): RiskLevel = when {
        count.total == 0 -> RiskLevel.SAFE
        count.absent * 100 > count.total * BAN_THRESHOLD_PERCENT -> RiskLevel.DANGER
        count.absent * 100 > count.total * WARNING_THRESHOLD_PERCENT -> RiskLevel.WARNING
        else -> RiskLevel.SAFE
    }

    fun riskLabel(risk: RiskLevel): String = when (risk) {
        RiskLevel.DANGER -> "Nguy cơ cấm thi"
        RiskLevel.WARNING -> "Cảnh báo chuyên cần"
        RiskLevel.SAFE -> "Đạt yêu cầu"
    }

    // e.g. "vắng 2/5 buổi (40%)", or "vắng không phép 1/5 buổi (20%), có phép 1 buổi"
    fun absenceSummary(count: StatusCount): String {
        val rate = "${count.absent}/${count.total} buổi (${count.absenceRate ?: 0}%)"
        return if (count.excused > 0) "vắng không phép $rate, có phép ${count.excused} buổi" else "vắng $rate"
    }

    // Text of the warning card on the student dashboard; empty when there is nothing to warn about
    fun studentWarning(count: StatusCount, risk: RiskLevel): String = when (risk) {
        RiskLevel.DANGER ->
            "Bạn đã ${absenceSummary(count)}, vượt ngưỡng $BAN_THRESHOLD_PERCENT% cho phép."
        RiskLevel.WARNING ->
            "Bạn đã ${absenceSummary(count)}. Vắng quá $BAN_THRESHOLD_PERCENT% số buổi sẽ có nguy cơ bị cấm thi."
        RiskLevel.SAFE -> ""
    }

    fun analyzeClass(data: ClassAttendanceData): ClassReport {
        val closedSessions = closedSessionsOf(data)
        val closedIds = closedSessions.map { it.id }.toSet()
        val rosterIds = data.students.map { it.id }.toSet()

        val statuses = HashMap<String, MutableMap<String, String>>()
        for (record in data.records) {
            if (record.sessionId !in closedIds || record.studentId !in rosterIds) continue
            if (!isKnownStatus(record.status)) continue
            statuses.getOrPut(record.studentId) { HashMap() }[record.sessionId] = record.status
        }

        val students = data.students
            .sortedWith(compareBy({ it.studentCode }, { it.name }))
            .map { student ->
                val count = StatusCount.of(statuses[student.id]?.values ?: emptyList())
                StudentStat(student, count, riskOf(count))
            }
        val sessions = closedSessions.map { session ->
            SessionStat(session, StatusCount.of(statuses.values.mapNotNull { it[session.id] }))
        }
        return ClassReport(data.courseClass, sessions, students, statuses)
    }

    fun analyzeStudent(studentId: String, classes: List<ClassAttendanceData>): List<StudentClassStat> {
        return classes
            .sortedBy { it.courseClass.name }
            .map { data ->
                val statusBySession = data.records
                    .filter { it.studentId == studentId && isKnownStatus(it.status) }
                    .associate { it.sessionId to it.status }
                val history = closedSessionsOf(data).mapNotNull { session ->
                    statusBySession[session.id]?.let { session to it }
                }
                val count = StatusCount.of(history.map { it.second })
                StudentClassStat(data.courseClass, count, riskOf(count), history)
            }
    }

    private fun closedSessionsOf(data: ClassAttendanceData): List<AttendanceSession> =
        data.sessions.filter { !it.isActive }.sortedBy { SessionDates.toMillis(it.date) }

    private fun isKnownStatus(status: String): Boolean =
        status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE ||
            status == AttendanceStatus.ABSENT || status == AttendanceStatus.EXCUSED
}
