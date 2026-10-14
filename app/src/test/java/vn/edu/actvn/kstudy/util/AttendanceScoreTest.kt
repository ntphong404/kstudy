package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.testutil.TestData

class AttendanceScoreTest {

    // --- The score itself ---

    @Test
    fun of_withoutAbsences_isTheFullTenPoints() {
        assertEquals(10.0, AttendanceScore.of(StatusCount(), 1.0), 0.0)
        assertEquals(10.0, AttendanceScore.of(StatusCount(present = 12), 1.0), 0.0)
    }

    @Test
    fun of_losesThePenaltyForEveryAbsenceWithoutPermission() {
        assertEquals(9.0, AttendanceScore.of(StatusCount(present = 4, absent = 1), 1.0), 0.0)
        assertEquals(7.0, AttendanceScore.of(StatusCount(present = 2, absent = 3), 1.0), 0.0)
        assertEquals(8.5, AttendanceScore.of(StatusCount(present = 2, absent = 3), 0.5), 0.0)
        assertEquals(4.0, AttendanceScore.of(StatusCount(absent = 3), 2.0), 0.0)
    }

    @Test
    fun of_beingLateOrExcused_costsNothing() {
        assertEquals(10.0, AttendanceScore.of(StatusCount(present = 1, late = 5, excused = 4), 1.0), 0.0)
        assertEquals(9.0, AttendanceScore.of(StatusCount(late = 2, absent = 1, excused = 3), 1.0), 0.0)
    }

    @Test
    fun of_neverGoesBelowZero() {
        assertEquals(0.0, AttendanceScore.of(StatusCount(absent = 11), 1.0), 0.0)
        assertEquals(0.0, AttendanceScore.of(StatusCount(absent = 3), 10.0), 0.0)
    }

    @Test
    fun of_penaltyOfZero_keepsEverybodyAtTen() {
        assertEquals(10.0, AttendanceScore.of(StatusCount(absent = 7), 0.0), 0.0)
    }

    @Test
    fun of_doesNotLeakFloatingPointNoise() {
        // 10 - 3 x 0.1 is 9.700000000000001 in binary floating point
        assertEquals(9.7, AttendanceScore.of(StatusCount(absent = 3), 0.1), 0.0)
        assertEquals("9,7", AttendanceScore.format(AttendanceScore.of(StatusCount(absent = 3), 0.1)))
    }

    // --- Text ---

    @Test
    fun format_writesWholeNumbersPlainAndDecimalsWithAComma() {
        assertEquals("10", AttendanceScore.format(10.0))
        assertEquals("0", AttendanceScore.format(0.0))
        assertEquals("8,5", AttendanceScore.format(8.5))
        assertEquals("9,25", AttendanceScore.format(9.25))
        assertEquals("9/10", AttendanceScore.outOfTen(9.0))
        assertEquals("7,5/10", AttendanceScore.outOfTen(7.5))
    }

    @Test
    fun rule_saysWhatAnAbsenceCosts() {
        assertEquals("Mỗi buổi vắng không phép trừ 1 điểm", AttendanceScore.rule(1.0))
        assertEquals("Mỗi buổi vắng không phép trừ 0,5 điểm", AttendanceScore.rule(0.5))
        assertEquals("trừ 0,5 điểm/buổi", AttendanceScore.shortRule(0.5))
    }

    // --- What a teacher types as the penalty ---

    @Test
    fun parsePenalty_acceptsPointOrCommaAsTheDecimalMark() {
        assertEquals(1.0, AttendanceScore.parsePenalty("1")!!, 0.0)
        assertEquals(0.5, AttendanceScore.parsePenalty("0.5")!!, 0.0)
        assertEquals(0.5, AttendanceScore.parsePenalty("0,5")!!, 0.0)
        assertEquals(2.0, AttendanceScore.parsePenalty(" 2 ")!!, 0.0)
        assertEquals(0.0, AttendanceScore.parsePenalty("0")!!, 0.0)
        assertEquals(10.0, AttendanceScore.parsePenalty("10")!!, 0.0)
        assertEquals("kept to two decimals", 0.33, AttendanceScore.parsePenalty("0.333")!!, 0.0)
    }

    @Test
    fun parsePenalty_rejectsWhatIsNotANumberFromZeroToTen() {
        assertNull(AttendanceScore.parsePenalty(""))
        assertNull(AttendanceScore.parsePenalty("một"))
        assertNull(AttendanceScore.parsePenalty("1,5,2"))
        assertNull(AttendanceScore.parsePenalty("-1"))
        assertNull(AttendanceScore.parsePenalty("10.5"))
        assertNull(AttendanceScore.parsePenalty("NaN"))
    }

    // --- In the reports ---

    @Test
    fun classReport_scoresEveryStudentWithThePenaltyOfTheClass() {
        // Absences without permission in the sample class: s1 none, s2 one, s3 two
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())
        assertEquals(listOf(10.0, 9.0, 8.0), report.students.map { report.scoreOf(it) })

        val base = TestData.sampleClass()
        val strict = AttendanceAnalyzer.analyzeClass(base.copy(courseClass = base.courseClass.copy(absencePenalty = 2.5)))
        assertEquals(listOf(10.0, 7.5, 5.0), strict.students.map { strict.scoreOf(it) })
    }

    @Test
    fun classReport_openSessionDoesNotCostPoints() {
        // Everybody is still stored as absent in the session that is open
        val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

        assertEquals(10.0, report.scoreOf(report.students.first { it.student.id == "s1" }), 0.0)
    }

    @Test
    fun studentStats_carryTheScoreOfEachClass() {
        val stats = AttendanceAnalyzer.analyzeStudent("s3", listOf(TestData.sampleClass(), TestData.emptyClass()))

        assertEquals(listOf("Anh văn B1", "Toán cao cấp A1"), stats.map { it.courseClass.name })
        assertEquals("a class without sessions starts at the full score", 10.0, stats[0].score, 0.0)
        assertEquals(8.0, stats[1].score, 0.0)
    }

    @Test
    fun defaultPenalty_isOnePointForEveryAbsence() {
        assertEquals(1.0, AttendanceScore.DEFAULT_PENALTY, 0.0)
        assertTrue(TestData.sampleClass().courseClass.absencePenalty == AttendanceScore.DEFAULT_PENALTY)
    }
}
