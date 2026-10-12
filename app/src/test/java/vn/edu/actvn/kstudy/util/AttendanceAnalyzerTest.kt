package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.ABSENT
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.EXCUSED
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.LATE
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.PRESENT
import vn.edu.actvn.kstudy.data.model.ClassAttendanceData
import vn.edu.actvn.kstudy.testutil.TestData

class AttendanceAnalyzerTest {

    // --- StatusCount ---

    @Test
    fun statusCount_computesTotalsAndRates() {
        val count = StatusCount(present = 3, late = 1, absent = 1)

        assertEquals(5, count.total)
        assertEquals(4, count.attended)
        assertEquals(80, count.attendanceRate)
        assertEquals(20, count.absenceRate)
    }

    @Test
    fun statusCount_withoutData_hasNoRate() {
        val count = StatusCount()

        assertEquals(0, count.total)
        assertNull(count.attendanceRate)
        assertNull(count.absenceRate)
    }

    @Test
    fun statusCount_roundsRateAndKeepsBothRatesSummingTo100() {
        val count = StatusCount(present = 2, late = 0, absent = 1)

        assertEquals(67, count.attendanceRate)
        assertEquals(33, count.absenceRate)
    }

    @Test
    fun statusCount_ofCountsOnlyKnownStatuses() {
        val count = StatusCount.of(listOf(PRESENT, PRESENT, LATE, ABSENT, EXCUSED, "", "UNKNOWN"))

        assertEquals(StatusCount(present = 2, late = 1, absent = 1, excused = 1), count)
    }

    @Test
    fun statusCount_plusAddsEachField() {
        val sum = StatusCount(1, 2, 3, 4) + StatusCount(10, 20, 30, 40)

        assertEquals(StatusCount(11, 22, 33, 44), sum)
    }

    @Test
    fun statusCount_excusedIsASessionHeldButNeitherAttendedNorAnAbsence() {
        val count = StatusCount(present = 3, late = 0, absent = 1, excused = 1)

        assertEquals(5, count.total)
        assertEquals(3, count.attended)
        assertEquals(60, count.attendanceRate)
        assertEquals("only the absence without permission", 20, count.absenceRate)
    }

    // --- Risk level ---

    @Test
    fun riskOf_withoutSessions_isSafe() {
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount()))
    }

    @Test
    fun riskOf_absentExactlyTenPercent_isStillSafe() {
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount(present = 9, absent = 1)))
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount(present = 90, absent = 10)))
    }

    @Test
    fun riskOf_absentAboveTenPercent_isWarning() {
        assertEquals(RiskLevel.WARNING, AttendanceAnalyzer.riskOf(StatusCount(present = 89, absent = 11)))
        assertEquals(RiskLevel.WARNING, AttendanceAnalyzer.riskOf(StatusCount(present = 7, absent = 1)))
    }

    @Test
    fun riskOf_absentExactlyTwentyPercent_isWarningNotDanger() {
        assertEquals(RiskLevel.WARNING, AttendanceAnalyzer.riskOf(StatusCount(present = 4, absent = 1)))
        assertEquals(RiskLevel.WARNING, AttendanceAnalyzer.riskOf(StatusCount(present = 80, absent = 20)))
    }

    @Test
    fun riskOf_absentAboveTwentyPercent_isDanger() {
        assertEquals(RiskLevel.DANGER, AttendanceAnalyzer.riskOf(StatusCount(present = 79, absent = 21)))
        assertEquals(RiskLevel.DANGER, AttendanceAnalyzer.riskOf(StatusCount(present = 7, absent = 2)))
        assertEquals(RiskLevel.DANGER, AttendanceAnalyzer.riskOf(StatusCount(absent = 1)))
    }

    @Test
    fun riskOf_lateDoesNotCountAsAbsent() {
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount(present = 0, late = 10, absent = 0)))
    }

    @Test
    fun riskOf_excusedAbsencesDoNotCountTowardsTheExamBan() {
        // 2 of 5 sessions missed: DANGER if both are unexcused, only WARNING once one is excused
        assertEquals(RiskLevel.DANGER, AttendanceAnalyzer.riskOf(StatusCount(present = 3, absent = 2)))
        assertEquals(RiskLevel.WARNING, AttendanceAnalyzer.riskOf(StatusCount(present = 3, absent = 1, excused = 1)))
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount(present = 3, absent = 0, excused = 2)))
        assertEquals(RiskLevel.SAFE, AttendanceAnalyzer.riskOf(StatusCount(excused = 4)))
    }

    @Test
    fun absenceSummary_describesAbsencesOverSessions() {
        val text = AttendanceAnalyzer.absenceSummary(StatusCount(present = 2, late = 1, absent = 2))

        assertEquals("vắng 2/5 buổi (40%)", text)
    }

    @Test
    fun absenceSummary_namesExcusedAbsencesSeparately() {
        val text = AttendanceAnalyzer.absenceSummary(StatusCount(present = 3, late = 0, absent = 1, excused = 1))

        assertEquals("vắng không phép 1/5 buổi (20%), có phép 1 buổi", text)
    }

    @Test
    fun studentWarning_explainsTheRiskToTheStudent() {
        val danger = StatusCount(present = 2, late = 1, absent = 2)
        val warning = StatusCount(present = 3, late = 1, absent = 1)

        assertEquals(
            "Bạn đã vắng 2/5 buổi (40%), vượt ngưỡng 20% cho phép.",
            AttendanceAnalyzer.studentWarning(danger, RiskLevel.DANGER)
        )
        assertEquals(
            "Bạn đã vắng 1/5 buổi (20%). Vắng quá 20% số buổi sẽ có nguy cơ bị cấm thi.",
            AttendanceAnalyzer.studentWarning(warning, RiskLevel.WARNING)
        )
        assertEquals("", AttendanceAnalyzer.studentWarning(StatusCount(present = 5), RiskLevel.SAFE))
    }

    @Test
    fun riskLabel_namesEveryLevel() {
        assertEquals("Nguy cơ cấm thi", AttendanceAnalyzer.riskLabel(RiskLevel.DANGER))
        assertEquals("Cảnh báo chuyên cần", AttendanceAnalyzer.riskLabel(RiskLevel.WARNING))
        assertEquals("Đạt yêu cầu", AttendanceAnalyzer.riskLabel(RiskLevel.SAFE))
    }

    // --- analyzeClass ---

    @Test
    fun analyzeClass_ordersClosedSessionsByDateAndSkipsOpenOne() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(listOf("d1", "d2", "d3", "d4", "d5"), report.sessions.map { it.session.id })
    }

    @Test
    fun analyzeClass_ordersStudentsByCode() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(listOf("SV001", "SV002", "SV003"), report.students.map { it.student.studentCode })
    }

    @Test
    fun analyzeClass_countsEachStudentOverClosedSessionsOnly() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())
        val byId = report.students.associateBy { it.student.id }

        assertEquals(StatusCount(present = 5, late = 0, absent = 0), byId.getValue("s1").count)
        assertEquals(StatusCount(present = 3, late = 1, absent = 1), byId.getValue("s2").count)
        assertEquals(StatusCount(present = 2, late = 1, absent = 2), byId.getValue("s3").count)
    }

    @Test
    fun analyzeClass_assignsRiskLevels() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())
        val byId = report.students.associateBy { it.student.id }

        assertEquals(RiskLevel.SAFE, byId.getValue("s1").risk)
        assertEquals(RiskLevel.WARNING, byId.getValue("s2").risk)
        assertEquals(RiskLevel.DANGER, byId.getValue("s3").risk)
    }

    @Test
    fun analyzeClass_listsAtRiskStudentsMostSeriousFirst() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(listOf("s3", "s2"), report.atRisk.map { it.student.id })
    }

    @Test
    fun analyzeClass_countsEachSessionOverCurrentRoster() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())
        val bySession = report.sessions.associate { it.session.id to it.count }

        // d1 also has an ABSENT record of a student who left the class: it must not be counted
        assertEquals(StatusCount(present = 2, late = 0, absent = 1), bySession.getValue("d1"))
        assertEquals(StatusCount(present = 1, late = 1, absent = 1), bySession.getValue("d2"))
        assertEquals(StatusCount(present = 2, late = 0, absent = 1), bySession.getValue("d3"))
        assertEquals(StatusCount(present = 2, late = 1, absent = 0), bySession.getValue("d4"))
        assertEquals(StatusCount(present = 3, late = 0, absent = 0), bySession.getValue("d5"))
    }

    @Test
    fun analyzeClass_overallIsTheSumOfAllStudents() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(StatusCount(present = 10, late = 2, absent = 3), report.overall)
        assertEquals(80, report.overall.attendanceRate)
        assertEquals(report.overall, report.sessions.fold(StatusCount()) { acc, s -> acc + s.count })
    }

    @Test
    fun analyzeClass_exposesStatusPerStudentAndSession() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(LATE, report.statusOf("s2", "d2"))
        assertEquals(ABSENT, report.statusOf("s3", "d1"))
        assertNull("open sessions are not part of the report", report.statusOf("s1", "open"))
        assertNull("students who left the class are not part of the report", report.statusOf("ghost", "d1"))
    }

    @Test
    fun analyzeClass_withoutSessions_isEmptyButKeepsRoster() {
        val report = AttendanceAnalyzer.analyzeClass(TestData.emptyClass())

        assertTrue(report.sessions.isEmpty())
        assertEquals(1, report.students.size)
        assertEquals(StatusCount(), report.overall)
        assertNull(report.overall.attendanceRate)
        assertTrue(report.atRisk.isEmpty())
    }

    @Test
    fun analyzeClass_studentWhoJoinedLate_isOnlyCountedForSessionsWithARecord() {
        val base = TestData.sampleClass()
        val newcomer = TestData.student(4)
        val data = base.copy(
            students = base.students + newcomer,
            records = base.records + TestData.record("d5", newcomer.id, PRESENT)
        )

        val stat = AttendanceAnalyzer.analyzeClass(data).students.first { it.student.id == newcomer.id }

        assertEquals(StatusCount(present = 1), stat.count)
        assertEquals(RiskLevel.SAFE, stat.risk)
    }

    @Test
    fun analyzeClass_approvedLeaveRequest_liftsTheStudentOutOfDanger() {
        val base = TestData.sampleClass()
        // The teacher approves s3's request for d1: that absence becomes an excused one
        val data = base.copy(
            records = base.records.map {
                if (it.sessionId == "d1" && it.studentId == "s3") it.copy(status = EXCUSED) else it
            }
        )

        val report = AttendanceAnalyzer.analyzeClass(data)
        val stat = report.students.first { it.student.id == "s3" }

        assertEquals(StatusCount(present = 2, late = 1, absent = 1, excused = 1), stat.count)
        assertEquals(RiskLevel.WARNING, stat.risk)
        assertEquals(EXCUSED, report.statusOf("s3", "d1"))
        assertEquals(StatusCount(present = 2, late = 0, absent = 0, excused = 1), report.sessions.first().count)
        assertEquals(StatusCount(present = 10, late = 2, absent = 2, excused = 1), report.overall)
    }

    @Test
    fun analyzeClass_onlyOpenSession_producesNoStatistics() {
        val base = TestData.sampleClass()
        val data = ClassAttendanceData(
            courseClass = base.courseClass,
            students = base.students,
            sessions = base.sessions.filter { it.isActive },
            records = base.records
        )

        val report = AttendanceAnalyzer.analyzeClass(data)

        assertTrue(report.sessions.isEmpty())
        assertEquals(StatusCount(), report.overall)
        assertTrue("nobody may be flagged while the only session is still open", report.atRisk.isEmpty())
    }

    // --- analyzeStudent ---

    @Test
    fun analyzeStudent_returnsOneEntryPerClassSortedByName() {
        val stats = AttendanceAnalyzer.analyzeStudent("s3", listOf(TestData.sampleClass(), TestData.emptyClass()))

        assertEquals(listOf("Anh văn B1", "Toán cao cấp A1"), stats.map { it.courseClass.name })
    }

    @Test
    fun analyzeStudent_countsOwnRecordsInClosedSessions() {
        val stat = AttendanceAnalyzer.analyzeStudent("s3", listOf(TestData.sampleClass())).single()

        assertEquals(StatusCount(present = 2, late = 1, absent = 2), stat.count)
        assertEquals(RiskLevel.DANGER, stat.risk)
        assertEquals(
            listOf("d1" to ABSENT, "d2" to ABSENT, "d3" to PRESENT, "d4" to LATE, "d5" to PRESENT),
            stat.history.map { it.first.id to it.second }
        )
    }

    @Test
    fun analyzeStudent_showsExcusedSessionsInTheHistory() {
        val base = TestData.sampleClass()
        val data = base.copy(
            records = base.records.map {
                if (it.sessionId == "d2" && it.studentId == "s3") it.copy(status = EXCUSED) else it
            }
        )

        val stat = AttendanceAnalyzer.analyzeStudent("s3", listOf(data)).single()

        assertEquals(StatusCount(present = 2, late = 1, absent = 1, excused = 1), stat.count)
        assertEquals(RiskLevel.WARNING, stat.risk)
        assertEquals(EXCUSED, stat.history[1].second)
    }

    @Test
    fun analyzeStudent_classWithoutSessions_hasNoHistoryAndIsSafe() {
        val stat = AttendanceAnalyzer.analyzeStudent("s3", listOf(TestData.emptyClass())).single()

        assertTrue(stat.history.isEmpty())
        assertEquals(StatusCount(), stat.count)
        assertEquals(RiskLevel.SAFE, stat.risk)
    }

    @Test
    fun analyzeStudent_unknownStudent_hasNoHistory() {
        val stat = AttendanceAnalyzer.analyzeStudent("nobody", listOf(TestData.sampleClass())).single()

        assertTrue(stat.history.isEmpty())
        assertEquals(RiskLevel.SAFE, stat.risk)
    }
}
