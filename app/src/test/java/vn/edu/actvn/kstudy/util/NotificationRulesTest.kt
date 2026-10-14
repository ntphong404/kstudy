package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.AppNotification
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.ABSENT
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.EXCUSED
import vn.edu.actvn.kstudy.data.model.AttendanceStatus.PRESENT
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.NotificationType
import vn.edu.actvn.kstudy.testutil.TestData
import java.util.Calendar

class NotificationRulesTest {

    private val now = 1_800_000_000_000L
    private val maths = CourseClass(id = "c1", name = "Toán cao cấp A1", teacherId = "t1", room = "Phòng 101")

    private fun day(year: Int, month: Int, dayOfMonth: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, hour, minute)
        }

    // --- A session is opened ---

    @Test
    fun sessionOpened_tellsEveryStudentOfTheClass() {
        val sent = NotificationRules.sessionOpened(maths, "sess1", listOf("s1", "s2", "s1"), now)

        assertEquals("a student listed twice is told once", listOf("s1", "s2"), sent.map { it.userId })
        val first = sent[0]
        assertEquals(NotificationType.SESSION_OPENED, first.type)
        assertEquals("Đang điểm danh • Toán cao cấp A1", first.title)
        assertTrue(first.body.contains("Quét QR"))
        assertEquals(now, first.createdAt)
        assertFalse(first.read)
    }

    @Test
    fun sessionOpened_openingTheSameSessionAgain_replacesInsteadOfAdding() {
        val first = NotificationRules.sessionOpened(maths, "sess1", listOf("s1"), now)
        val again = NotificationRules.sessionOpened(maths, "sess1", listOf("s1"), now + 60_000)
        val otherSession = NotificationRules.sessionOpened(maths, "sess2", listOf("s1"), now)

        assertEquals(first.single().id, again.single().id)
        assertTrue(first.single().id != otherSession.single().id)
    }

    // --- A session is closed ---

    // In the sample class the last closed session is d5 (29/09/2026), where everybody was present,
    // and d3 (15/09/2026), where only s2 was absent
    private val report = AttendanceAnalyzer.analyzeClass(TestData.sampleClass())

    @Test
    fun sessionClosed_tellsOnlyTheStudentsLeftAbsent() {
        val notices = NotificationRules.sessionClosed(report, "d3", now)

        val sent = notices.send.single()
        assertEquals("s2", sent.userId)
        assertEquals(NotificationType.ABSENCE, sent.type)
        assertEquals(NotificationRules.absenceId("d3", "s2"), sent.id)
        assertTrue(sent.body.startsWith("Bạn bị ghi vắng buổi 15/09/2026. Điểm chuyên cần còn 9/10."))
    }

    @Test
    fun sessionClosed_titleAndWarningFollowTheRiskOfTheStudent() {
        // d1: s3 absent; over the five sessions s3 missed 2 (40%, at risk of the exam ban)
        val danger = NotificationRules.sessionClosed(report, "d1", now).send.single { it.userId == "s3" }
        assertEquals("Nguy cơ cấm thi • Toán cao cấp A1", danger.title)
        assertTrue(danger.body.contains("Điểm chuyên cần còn 8/10."))
        assertTrue(danger.body.contains("vượt ngưỡng 20% cho phép"))

        // s2 missed 1 of 5 (20%): a warning, not yet the ban
        val warning = NotificationRules.sessionClosed(report, "d3", now).send.single()
        assertEquals("Cảnh báo chuyên cần • Toán cao cấp A1", warning.title)
        assertTrue(warning.body.contains("Vắng quá 20% số buổi"))
    }

    @Test
    fun sessionClosed_firstAbsenceOfMany_isAPlainNoticeWithTheScore() {
        // One absence in twelve sessions is under the 10% that starts the warnings
        val base = TestData.sampleClass()
        val sessions = (1..12).map { TestData.session("x$it", "%02d/09/2026 07:30".format(it)) }
        val records = sessions.mapIndexed { index, session ->
            TestData.record(session.id, "s1", if (index == 11) ABSENT else PRESENT)
        }
        val data = base.copy(sessions = sessions, records = records)

        val sent = NotificationRules.sessionClosed(AttendanceAnalyzer.analyzeClass(data), "x12", now).send.single()

        assertEquals("Vắng buổi học • Toán cao cấp A1", sent.title)
        assertEquals("Bạn bị ghi vắng buổi 12/09/2026. Điểm chuyên cần còn 9/10.", sent.body)
    }

    @Test
    fun sessionClosed_usesThePenaltyOfTheClass() {
        val base = TestData.sampleClass()
        val strict = AttendanceAnalyzer.analyzeClass(base.copy(courseClass = base.courseClass.copy(absencePenalty = 2.5)))

        val sent = NotificationRules.sessionClosed(strict, "d3", now).send.single()

        assertTrue(sent.body.contains("Điểm chuyên cần còn 7,5/10."))
    }

    @Test
    fun sessionClosed_excusedAbsence_isNotReported() {
        val base = TestData.sampleClass()
        val data = base.copy(
            records = base.records.map {
                if (it.sessionId == "d3" && it.studentId == "s2") it.copy(status = EXCUSED) else it
            }
        )

        val notices = NotificationRules.sessionClosed(AttendanceAnalyzer.analyzeClass(data), "d3", now)

        assertTrue(notices.send.isEmpty())
    }

    @Test
    fun sessionClosed_withdrawsTheNoticeOfStudentsWhoAreNoLongerAbsent() {
        // A session reopened for a late student and closed again: that student's old notice must go
        val notices = NotificationRules.sessionClosed(report, "d3", now)

        assertEquals(
            setOf(NotificationRules.absenceId("d3", "s1"), NotificationRules.absenceId("d3", "s3")),
            notices.withdrawIds.toSet()
        )
        assertFalse(notices.withdrawIds.contains(notices.send.single().id))
    }

    @Test
    fun sessionClosed_sessionThatIsStillOpenOrUnknown_sendsNothing() {
        for (sessionId in listOf("open", "missing")) {
            val notices = NotificationRules.sessionClosed(report, sessionId, now)
            assertTrue(notices.send.isEmpty())
            assertTrue(notices.withdrawIds.isEmpty())
        }
    }

    // --- Leave requests ---

    private val request = LeaveRequest(
        id = "r1", classId = "c1", studentId = "s1", studentName = "Học sinh 1", studentCode = "SV001",
        date = "20/10/2026", reason = "Em bị sốt"
    )

    @Test
    fun leaveSubmitted_goesToTheTeacherOfTheClass() {
        val sent = NotificationRules.leaveSubmitted(request, maths, now)!!

        assertEquals("t1", sent.userId)
        assertEquals(NotificationType.LEAVE_SUBMITTED, sent.type)
        assertEquals("Đơn xin nghỉ mới • Toán cao cấp A1", sent.title)
        assertEquals("Học sinh 1 (SV001) xin nghỉ ngày 20/10/2026. Lý do: Em bị sốt", sent.body)
        assertEquals("every request is a notification of its own", "", sent.id)
    }

    @Test
    fun leaveSubmitted_classWithoutATeacher_hasNobodyToTell() {
        assertNull(NotificationRules.leaveSubmitted(request, maths.copy(teacherId = ""), now))
    }

    @Test
    fun leaveDecided_tellsTheStudentTheOutcome() {
        val approved = NotificationRules.leaveDecided(request, "Toán cao cấp A1", approved = true, now = now)
        val rejected = NotificationRules.leaveDecided(request, "Toán cao cấp A1", approved = false, now = now)

        assertEquals("s1", approved.userId)
        assertEquals(NotificationType.LEAVE_DECIDED, approved.type)
        assertEquals("Đơn xin nghỉ đã được duyệt • Toán cao cấp A1", approved.title)
        assertTrue(approved.body.contains("ngày 20/10/2026"))
        assertTrue(approved.body.contains("vắng có phép"))

        assertEquals("Đơn xin nghỉ bị từ chối • Toán cao cấp A1", rejected.title)
        assertTrue(rejected.body.contains("vắng không phép"))
        assertEquals(NotificationRules.decidedId("r1"), rejected.id)
    }

    @Test
    fun leaveDecided_withoutAClassName_stillReads() {
        assertEquals(
            "Đơn xin nghỉ đã được duyệt",
            NotificationRules.leaveDecided(request, "", approved = true, now = now).title
        )
    }

    // --- Class reminders ---

    // 14/10/2026 is a Wednesday
    private val wednesdayNoon = day(2026, Calendar.OCTOBER, 14, 12, 0)

    @Test
    fun classReminders_areSetFifteenMinutesBeforeEachUpcomingLesson() {
        val classes = listOf(maths.copy(schedule = "Thứ 2, Thứ 5 (07:30-09:00)"))

        val reminders = NotificationRules.classReminders(classes, wednesdayNoon)

        assertEquals(
            listOf(
                day(2026, Calendar.OCTOBER, 15, 7, 15).timeInMillis, // Thursday
                day(2026, Calendar.OCTOBER, 19, 7, 15).timeInMillis // Monday of next week
            ),
            reminders.map { it.atMillis }
        )
        assertEquals("Sắp đến giờ lên lớp • Toán cao cấp A1", reminders[0].title)
        assertEquals("Bắt đầu lúc 07:30 tại Phòng 101, còn 15 phút nữa.", reminders[0].body)
    }

    @Test
    fun classReminders_lessonLaterToday_isRemindedToday() {
        val classes = listOf(maths.copy(schedule = "Thứ 4 (13:30-15:30)"))

        val reminder = NotificationRules.classReminders(classes, wednesdayNoon).single()

        assertEquals(day(2026, Calendar.OCTOBER, 14, 13, 15).timeInMillis, reminder.atMillis)
    }

    @Test
    fun classReminders_lessonAboutToStartOrAlreadyStarted_waitsForNextWeek() {
        val classes = listOf(maths.copy(schedule = "Thứ 4 (13:30-15:30)"))

        // 13:20: the lesson is still ahead, but the moment to remind (13:15) has passed
        assertTrue(NotificationRules.classReminders(classes, day(2026, Calendar.OCTOBER, 14, 13, 20)).isEmpty())
        // 14:00: today's lesson is under way, the next one is in a week
        assertEquals(
            listOf(day(2026, Calendar.OCTOBER, 21, 13, 15).timeInMillis),
            NotificationRules.classReminders(classes, day(2026, Calendar.OCTOBER, 14, 14, 0)).map { it.atMillis }
        )
    }

    @Test
    fun classReminders_roomIsWordedNaturallyOrLeftOut() {
        val reminders = NotificationRules.classReminders(
            listOf(
                maths.copy(name = "A", room = "101", schedule = "Thứ 5 (07:30-09:00)"),
                maths.copy(name = "B", room = "", schedule = "Thứ 5 (09:30-11:00)")
            ),
            wednesdayNoon
        )

        assertEquals("Bắt đầu lúc 07:30 tại phòng 101, còn 15 phút nữa.", reminders[0].body)
        assertEquals("Bắt đầu lúc 09:30, còn 15 phút nữa.", reminders[1].body)
    }

    @Test
    fun classReminders_classWithoutAUsableSchedule_hasNone() {
        val classes = listOf(maths.copy(schedule = ""), maths.copy(schedule = "Thứ 2"), maths.copy(schedule = "(07:30-09:00)"))

        assertTrue(NotificationRules.classReminders(classes, wednesdayNoon).isEmpty())
    }

    // --- The inbox and what the phone announces ---

    private fun notification(id: String, createdAt: Long, read: Boolean = false) =
        AppNotification(id = id, userId = "s1", title = id, createdAt = createdAt, read = read)

    @Test
    fun newestFirst_ordersTheInbox() {
        val list = listOf(notification("a", 10), notification("c", 30), notification("b", 20))

        assertEquals(listOf("c", "b", "a"), NotificationRules.newestFirst(list).map { it.id })
    }

    @Test
    fun toAnnounce_isWhatIsUnreadAndNotAnnouncedBefore_oldestFirst() {
        val list = listOf(
            notification("new2", 40),
            notification("seen", 30),
            notification("read", 20, read = true),
            notification("new1", 10)
        )

        val fresh = NotificationRules.toAnnounce(list, announcedIds = setOf("seen"))

        assertEquals(listOf("new1", "new2"), fresh.map { it.id })
    }

    @Test
    fun toAnnounce_nothingNew_announcesNothing() {
        val list = listOf(notification("a", 10), notification("b", 20, read = true))

        assertTrue(NotificationRules.toAnnounce(list, setOf("a", "b")).isEmpty())
        assertTrue(NotificationRules.toAnnounce(emptyList(), emptySet()).isEmpty())
    }

    // --- Keeping the status bar in line with the notifications ---

    @Test
    fun planAnnouncements_postsWhatIsNewAndRemembersEverythingThatExists() {
        val list = listOf(notification("new", 20), notification("seen", 10), notification("read", 5, read = true))

        val plan = NotificationRules.planAnnouncements(list, announcedIds = setOf("seen"), canNotify = true)

        assertEquals(listOf("new"), plan.post.map { it.id })
        assertEquals(setOf("new", "seen", "read"), plan.remember)
        assertTrue(plan.withdraw.isEmpty())
    }

    @Test
    fun planAnnouncements_withoutPermission_keepsNewNotificationsForLater() {
        // At the first sign-in the notifications arrive while Android is still asking the user
        val list = listOf(notification("a", 10), notification("b", 20))

        val blocked = NotificationRules.planAnnouncements(list, announcedIds = emptySet(), canNotify = false)
        assertTrue(blocked.post.isEmpty())
        assertTrue("nothing counts as shown", blocked.remember.isEmpty())

        // The user allows notifications: both still come up
        val allowed = NotificationRules.planAnnouncements(list, blocked.remember, canNotify = true)
        assertEquals(listOf("a", "b"), allowed.post.map { it.id })
    }

    @Test
    fun planAnnouncements_withoutPermission_stillForgetsWhatNoLongerExists() {
        val plan = NotificationRules.planAnnouncements(
            listOf(notification("kept", 10)), announcedIds = setOf("kept", "deleted"), canNotify = false
        )

        assertEquals(setOf("kept"), plan.remember)
        assertEquals(setOf("deleted"), plan.withdraw)
    }

    @Test
    fun planAnnouncements_withdrawsWhatWasDeletedOrRead() {
        // A session closed again after a late student scanned: that student's absence notice is
        // deleted, and the warning must not stay in the status bar
        val list = listOf(notification("unread", 30), notification("readInTheApp", 20, read = true))

        val plan = NotificationRules.planAnnouncements(
            list, announcedIds = setOf("unread", "readInTheApp", "withdrawnAbsence"), canNotify = true
        )

        assertEquals(setOf("readInTheApp", "withdrawnAbsence"), plan.withdraw)
        assertTrue(plan.post.isEmpty())
        assertFalse("the notification is gone for good", plan.remember.contains("withdrawnAbsence"))
    }

    @Test
    fun planAnnouncements_neverAnnouncedId_isNotWithdrawn() {
        val plan = NotificationRules.planAnnouncements(emptyList(), announcedIds = emptySet(), canNotify = true)

        assertTrue(plan.post.isEmpty())
        assertTrue(plan.withdraw.isEmpty())
        assertTrue(plan.remember.isEmpty())
    }

    // --- Weekly reminders ---

    @Test
    fun nextWeek_isTheSameTimeSevenDaysLater() {
        val monday = day(2026, Calendar.OCTOBER, 19, 7, 15).timeInMillis

        assertEquals(
            day(2026, Calendar.OCTOBER, 26, 7, 15).timeInMillis,
            NotificationRules.nextWeek(monday, nowMillis = monday + 60_000)
        )
    }

    @Test
    fun nextWeek_afterThePhoneWasOffForWeeks_skipsToTheNextOneAhead() {
        val monday = day(2026, Calendar.OCTOBER, 19, 7, 15).timeInMillis
        val threeWeeksLater = day(2026, Calendar.NOVEMBER, 9, 12, 0).timeInMillis

        assertEquals(
            day(2026, Calendar.NOVEMBER, 16, 7, 15).timeInMillis,
            NotificationRules.nextWeek(monday, threeWeeksLater)
        )
    }

    @Test
    fun timeLabel_showsTheDayOnlyWhenItIsNotToday() {
        val nowAt = day(2026, Calendar.OCTOBER, 14, 15, 0).timeInMillis

        assertEquals("10:05", NotificationRules.timeLabel(day(2026, Calendar.OCTOBER, 14, 10, 5).timeInMillis, nowAt))
        assertEquals("13/10 22:40", NotificationRules.timeLabel(day(2026, Calendar.OCTOBER, 13, 22, 40).timeInMillis, nowAt))
        assertEquals("14/10 10:05", NotificationRules.timeLabel(day(2025, Calendar.OCTOBER, 14, 10, 5).timeInMillis, nowAt))
    }
}
