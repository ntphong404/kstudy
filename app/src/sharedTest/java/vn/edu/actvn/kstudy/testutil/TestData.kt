package vn.edu.actvn.kstudy.testutil

import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.ABSENT
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.LATE
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.PRESENT
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Student

object TestData {

    const val OPEN_SESSION_DATE = "06/10/2026 07:31"

    fun student(n: Int) = Student(
        id = "s$n",
        userId = "s$n",
        name = "Học sinh $n",
        studentCode = "SV%03d".format(n),
        className = "K18"
    )

    fun session(id: String, date: String, classId: String = "c1", isActive: Boolean = false) =
        AttendanceSession(id = id, classId = classId, date = date, qrCode = "AB12", isActive = isActive)

    fun record(sessionId: String, studentId: String, status: String) = AttendanceRecord(
        id = "$sessionId-$studentId",
        sessionId = sessionId,
        studentId = studentId,
        studentName = "",
        studentCode = "",
        status = status,
        timeMarked = if (status == ABSENT) "-" else "07:35"
    )

    /**
     * Three students, five closed sessions and one session that is still open.
     *
     *            d1  d2  d3  d4  d5   present/late/absent   expected
     * s1 (SV001)  P   P   P   P   P        5 / 0 / 0        SAFE,    100%
     * s2 (SV002)  P   L   A   P   P        3 / 1 / 1        WARNING,  80% (absent exactly 20%)
     * s3 (SV003)  A   A   P   L   P        2 / 1 / 2        DANGER,   60% (absent 40%)
     *
     * Whole class: 10 present, 2 late, 3 absent => 80%.
     * The data also holds things that must be ignored: the open session (everybody still ABSENT),
     * a record of a student who left the class and a record with an empty status.
     */
    fun sampleClass(): ClassAttendanceData {
        val courseClass = CourseClass(
            id = "c1",
            name = "Toán cao cấp A1",
            academicYear = "2025-2026",
            teacherId = "t1",
            room = "Phòng 101",
            schedule = "Thứ 2 (07:30-09:00)",
            totalStudents = 3,
            teacherName = "Nguyễn Văn An"
        )
        // Deliberately unordered: the analyzer has to sort students by code and sessions by date
        val students = listOf(student(3), student(1), student(2))
        val sessions = listOf(
            session("d3", "15/09/2026 07:32"),
            session("d1", "01/09/2026"), // old format without a time
            session("open", OPEN_SESSION_DATE, isActive = true),
            session("d5", "29/09/2026 07:30"),
            session("d2", "08/09/2026 07:31"),
            session("d4", "22/09/2026 07:33")
        )
        val matrix = mapOf(
            "s1" to listOf(PRESENT, PRESENT, PRESENT, PRESENT, PRESENT),
            "s2" to listOf(PRESENT, LATE, ABSENT, PRESENT, PRESENT),
            "s3" to listOf(ABSENT, ABSENT, PRESENT, LATE, PRESENT)
        )
        val records = mutableListOf<AttendanceRecord>()
        for ((studentId, statuses) in matrix) {
            statuses.forEachIndexed { index, status ->
                records.add(record("d${index + 1}", studentId, status))
            }
            records.add(record("open", studentId, ABSENT))
        }
        records.add(record("d1", "ghost", ABSENT))
        records.add(record("d2", "ghost", ""))
        return ClassAttendanceData(courseClass, students, sessions, records)
    }

    // A second class of student s3, without any attendance session yet
    fun emptyClass(): ClassAttendanceData = ClassAttendanceData(
        courseClass = CourseClass(
            id = "c2",
            name = "Anh văn B1",
            academicYear = "2025-2026",
            teacherId = "t2",
            room = "Phòng 202",
            schedule = "Thứ 5 (13:30-15:30)",
            totalStudents = 1,
            teacherName = "Trần Thị Bình"
        ),
        students = listOf(student(3)),
        sessions = emptyList(),
        records = emptyList()
    )
}
