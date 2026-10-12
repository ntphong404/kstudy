package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.AttendanceSession
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.ABSENT
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.EXCUSED
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.LATE
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.PRESENT
import vn.edu.actvn.kstudy.data.model.GeoLocation
import vn.edu.actvn.kstudy.util.CheckInRules.Position
import vn.edu.actvn.kstudy.util.CheckInRules.Result

class CheckInRulesTest {

    private val openedAt = 1_791_792_000_000L
    private val minute = 60_000L

    // Roughly the Academy of Cryptography Techniques, Hanoi
    private val classroom = GeoLocation(20.9805, 105.7960)

    // 0.0005 degrees of latitude is about 56 m, 0.002 degrees about 222 m
    private val inTheRoom = Position(GeoLocation(20.9805, 105.7960))
    private val nextDoor = Position(GeoLocation(20.9810, 105.7960))
    private val atHome = Position(GeoLocation(20.9825, 105.7960))

    // 0.00135 degrees of latitude is about 150 m: outside the radius, but close enough for GPS error to explain
    private val acrossTheYard = GeoLocation(20.98185, 105.7960)

    private fun session(
        lateAfterMinutes: Int = 10,
        location: GeoLocation? = null,
        locationAccuracyMeters: Float = 0f,
        openedAt: Long = this.openedAt,
        date: String = ""
    ) = AttendanceSession(
        id = "sess",
        classId = "c1",
        date = date,
        secret = "secret",
        openedAt = openedAt,
        lateAfterMinutes = lateAfterMinutes,
        location = location,
        locationAccuracyMeters = locationAccuracyMeters
    )

    private fun record(studentId: String, status: String = ABSENT, deviceId: String = "") = AttendanceRecord(
        id = "rec-$studentId",
        sessionId = "sess",
        studentId = studentId,
        status = status,
        deviceId = deviceId
    )

    private val roster = listOf(record("s1"), record("s2"), record("s3"))

    private fun check(
        session: AttendanceSession = session(),
        records: List<AttendanceRecord> = roster,
        studentId: String = "s1",
        deviceId: String = "phone-1",
        position: Position? = null,
        now: Long = openedAt + minute
    ) = CheckInRules.check(session, records, studentId, deviceId, position, now)

    private fun assertRejected(result: Result, messagePart: String) {
        assertTrue("expected a rejection but got $result", result is Result.Rejected)
        val message = (result as Result.Rejected).message
        assertTrue("\"$message\" should contain \"$messagePart\"", message.contains(messagePart))
    }

    // --- Accepting ---

    @Test
    fun check_onTime_marksTheStudentsOwnRecordPresent() {
        assertEquals(Result.Accepted("rec-s1", PRESENT), check())
        assertEquals(Result.Accepted("rec-s2", PRESENT), check(studentId = "s2"))
    }

    @Test
    fun check_studentWithApprovedLeaveWhoComesAnyway_isAccepted() {
        val records = listOf(record("s1", status = EXCUSED))

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(records = records))
    }

    // --- Late ---

    @Test
    fun check_afterTheThreshold_marksLate() {
        assertEquals(Result.Accepted("rec-s1", LATE), check(now = openedAt + 10 * minute + 1))
        assertEquals(Result.Accepted("rec-s1", LATE), check(now = openedAt + 45 * minute))
    }

    @Test
    fun check_exactlyAtTheThreshold_isStillOnTime() {
        assertEquals(Result.Accepted("rec-s1", PRESENT), check(now = openedAt + 10 * minute))
    }

    @Test
    fun statusAt_usesTheThresholdChosenByTheTeacher() {
        val strict = session(lateAfterMinutes = 1)

        assertEquals(PRESENT, CheckInRules.statusAt(strict, openedAt + 59_000))
        assertEquals(LATE, CheckInRules.statusAt(strict, openedAt + 61_000))
    }

    @Test
    fun statusAt_withoutThreshold_isNeverLate() {
        assertEquals(PRESENT, CheckInRules.statusAt(session(lateAfterMinutes = 0), openedAt + 500 * minute))
    }

    @Test
    fun statusAt_sessionWithoutOpeningTime_fallsBackToItsDate() {
        val legacy = session(openedAt = 0, date = "12/10/2026 08:00")
        val opened = SessionDates.toMillis("12/10/2026 08:00")

        assertEquals(PRESENT, CheckInRules.statusAt(legacy, opened + 9 * minute))
        assertEquals(LATE, CheckInRules.statusAt(legacy, opened + 11 * minute))
    }

    @Test
    fun statusAt_sessionWithNoUsableOpeningTime_isNeverLate() {
        val broken = session(openedAt = 0, date = "")

        assertEquals(PRESENT, CheckInRules.statusAt(broken, openedAt + 500 * minute))
    }

    // --- Own record ---

    @Test
    fun check_studentWithoutARecord_isRejected() {
        assertRejected(check(studentId = "stranger"), "Không tìm thấy thông tin điểm danh")
    }

    @Test
    fun check_studentWhoAlreadyCheckedIn_isRejected() {
        assertRejected(check(records = listOf(record("s1", status = PRESENT))), "đã điểm danh")
        assertRejected(check(records = listOf(record("s1", status = LATE))), "đã điểm danh")
    }

    // --- One phone, one account ---

    @Test
    fun check_phoneAlreadyUsedByAnotherAccount_isRejected() {
        val records = listOf(record("s1"), record("s2", status = PRESENT, deviceId = "phone-1"))

        assertRejected(check(records = records, studentId = "s1", deviceId = "phone-1"), "Thiết bị này đã được dùng")
    }

    @Test
    fun check_phoneUsedByAnotherAccount_staysBlockedEvenAfterTheTeacherMarksThatAccountAbsent() {
        val records = listOf(record("s1"), record("s2", status = ABSENT, deviceId = "phone-1"))

        assertRejected(check(records = records, studentId = "s1", deviceId = "phone-1"), "Thiết bị này đã được dùng")
    }

    @Test
    fun check_differentPhones_doNotBlockEachOther() {
        val records = listOf(record("s1"), record("s2", status = PRESENT, deviceId = "phone-2"))

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(records = records, deviceId = "phone-1"))
    }

    @Test
    fun check_recordsMarkedByTheTeacher_haveNoPhoneAndBlockNobody() {
        val records = listOf(record("s1"), record("s2", status = PRESENT, deviceId = ""))

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(records = records, deviceId = ""))
    }

    // --- Location ---

    @Test
    fun check_sessionWithoutLocation_doesNotNeedAPosition() {
        assertEquals(Result.Accepted("rec-s1", PRESENT), check(session = session(location = null), position = null))
    }

    @Test
    fun check_insideTheRadius_isAccepted() {
        val withLocation = session(location = classroom)

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(session = withLocation, position = inTheRoom))
        assertEquals(Result.Accepted("rec-s1", PRESENT), check(session = withLocation, position = nextDoor))
    }

    @Test
    fun check_outsideTheRadius_isRejectedWithTheDistance() {
        val result = check(session = session(location = classroom), position = atHome)

        assertRejected(result, "cách lớp học khoảng 222 m")
        assertRejected(result, "ngoài phạm vi 100 m")
    }

    @Test
    fun check_exactPositionOutsideTheRadius_isRejected() {
        val exact = Position(acrossTheYard, accuracyMeters = 0f)

        assertRejected(check(session = session(location = classroom), position = exact), "cách lớp học khoảng 150 m")
    }

    @Test
    fun check_distanceThatThePhonesUncertaintyExplains_isAccepted() {
        // Measured 150 m away, but the phone only knows where it is to within 60 m
        val vague = Position(acrossTheYard, accuracyMeters = 60f)

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(session = session(location = classroom), position = vague))
    }

    @Test
    fun check_uncertaintyTooSmallToExplainTheDistance_isRejected() {
        val slightlyVague = Position(acrossTheYard, accuracyMeters = 40f)

        assertRejected(check(session = session(location = classroom), position = slightlyVague), "ngoài phạm vi 100 m")
    }

    @Test
    fun check_teachersUncertaintyCountsAsWell() {
        val withVagueTeacher = session(location = classroom, locationAccuracyMeters = 30f)
        val student = Position(acrossTheYard, accuracyMeters = 30f)

        assertEquals(Result.Accepted("rec-s1", PRESENT), check(session = withVagueTeacher, position = student))
    }

    @Test
    fun check_veryVaguePosition_doesNotLetSomebodyFarAwayIn() {
        // 222 m away: even with a position known only to within 2 km the allowance stops at 100 m
        val veryVague = Position(atHome.location, accuracyMeters = 2000f)
        val vagueTeacher = session(location = classroom, locationAccuracyMeters = 2000f)

        assertRejected(check(session = vagueTeacher, position = veryVague), "cách lớp học khoảng 222 m")
    }

    @Test
    fun check_positionUnavailable_isRejected() {
        assertRejected(check(session = session(location = classroom), position = null), "Không xác định được vị trí")
    }

    @Test
    fun check_fakeGpsApp_isRejectedEvenInsideTheRadius() {
        val faked = Position(classroom, isMock = true)

        assertRejected(check(session = session(location = classroom), position = faked), "vị trí giả lập")
    }

    @Test
    fun distanceMeters_matchesKnownDistances() {
        assertEquals(0.0, CheckInRules.distanceMeters(classroom, classroom), 0.001)
        // One thousandth of a degree of latitude is 111.2 m anywhere on Earth
        assertEquals(111.2, CheckInRules.distanceMeters(classroom, GeoLocation(20.9815, 105.7960)), 0.5)
        // Hanoi to Ho Chi Minh City, about 1 140 km in a straight line
        val hanoi = GeoLocation(21.0285, 105.8542)
        val hoChiMinhCity = GeoLocation(10.8231, 106.6297)
        assertEquals(1_138_000.0, CheckInRules.distanceMeters(hanoi, hoChiMinhCity), 5_000.0)
    }

    @Test
    fun distanceMeters_isSymmetric() {
        assertEquals(
            CheckInRules.distanceMeters(classroom, atHome.location),
            CheckInRules.distanceMeters(atHome.location, classroom),
            0.001
        )
    }
}
