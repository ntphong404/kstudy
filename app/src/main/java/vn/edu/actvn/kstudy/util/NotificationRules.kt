package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.AppNotification
import vn.edu.actvn.kstudy.data.model.AttendanceStatus
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.NotificationType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// What closing a session means for the notifications of its class
data class ClosedSessionNotices(
    val send: List<AppNotification>, // to the students recorded as absent
    val withdrawIds: List<String> // absence notices of this session that no longer hold
)

// See NotificationRules.planAnnouncements
data class AnnouncementPlan(
    val post: List<AppNotification>, // to show in the status bar, oldest first
    val withdraw: Set<String>, // ids whose notification has to leave the status bar
    val remember: Set<String> // the ids to keep as dealt with
)

// A reminder the phone raises by itself shortly before a lesson
data class ClassReminder(
    val atMillis: Long,
    val title: String,
    val body: String
)

// Who is told what, and when the phone should announce it. Kept free of Android and Firestore
// so it can be unit tested.
object NotificationRules {

    const val REMINDER_MINUTES = 15

    // A notification about a session has an id made from the session and the student: opening
    // or closing the same session twice replaces the notification instead of adding another one
    fun openedId(sessionId: String, studentId: String) = "opened_${sessionId}_$studentId"
    fun absenceId(sessionId: String, studentId: String) = "absence_${sessionId}_$studentId"
    fun decidedId(requestId: String) = "leave_decided_$requestId"

    // To every student of the class, the moment the teacher opens the attendance session
    fun sessionOpened(
        courseClass: CourseClass,
        sessionId: String,
        studentIds: List<String>,
        now: Long
    ): List<AppNotification> = studentIds.distinct().map { studentId ->
        AppNotification(
            id = openedId(sessionId, studentId),
            userId = studentId,
            type = NotificationType.SESSION_OPENED,
            title = "Đang điểm danh • ${courseClass.name}",
            body = "Giáo viên vừa mở phiên điểm danh. Mở mục Quét QR để điểm danh ngay.",
            createdAt = now
        )
    }

    // To the students the closed session leaves absent without permission: what it did to
    // their attendance score and, once they are at risk, the warning. `report` is the class
    // analysed after the session was closed.
    fun sessionClosed(report: ClassReport, sessionId: String, now: Long): ClosedSessionNotices {
        val session = report.sessions.firstOrNull { it.session.id == sessionId }?.session
            ?: return ClosedSessionNotices(emptyList(), emptyList())
        val className = report.courseClass.name
        val day = SessionDates.dateOnly(session.date)

        val (absent, others) = report.students.partition {
            report.statusOf(it.student.id, sessionId) == AttendanceStatus.ABSENT
        }
        val send = absent.map { stat ->
            val title = when (stat.risk) {
                RiskLevel.SAFE -> "Vắng buổi học"
                else -> AttendanceAnalyzer.riskLabel(stat.risk)
            }
            val warning = AttendanceAnalyzer.studentWarning(stat.count, stat.risk)
            AppNotification(
                id = absenceId(sessionId, stat.student.id),
                userId = stat.student.id,
                type = NotificationType.ABSENCE,
                title = "$title • $className",
                body = listOf(
                    "Bạn bị ghi vắng buổi $day. Điểm chuyên cần còn ${AttendanceScore.outOfTen(report.scoreOf(stat))}.",
                    warning
                ).filter { it.isNotEmpty() }.joinToString(" "),
                createdAt = now
            )
        }
        // A session can be reopened for a student who arrives late and then closed again: the
        // notice from the first closing would otherwise still call that student absent
        return ClosedSessionNotices(send, others.map { absenceId(sessionId, it.student.id) })
    }

    // To the teacher of the class when a student asks for leave; null for a class without a teacher
    fun leaveSubmitted(request: LeaveRequest, courseClass: CourseClass, now: Long): AppNotification? {
        if (courseClass.teacherId.isBlank()) return null
        return AppNotification(
            userId = courseClass.teacherId,
            type = NotificationType.LEAVE_SUBMITTED,
            title = "Đơn xin nghỉ mới • ${courseClass.name}",
            body = "${request.studentName} (${request.studentCode}) xin nghỉ ngày ${request.date}. " +
                "Lý do: ${request.reason}",
            createdAt = now
        )
    }

    // To the student once the teacher has decided
    fun leaveDecided(request: LeaveRequest, className: String, approved: Boolean, now: Long): AppNotification {
        val forClass = if (className.isBlank()) "" else " • $className"
        return AppNotification(
            id = decidedId(request.id),
            userId = request.studentId,
            type = NotificationType.LEAVE_DECIDED,
            title = (if (approved) "Đơn xin nghỉ đã được duyệt" else "Đơn xin nghỉ bị từ chối") + forClass,
            body = if (approved) {
                "Giáo viên đã duyệt đơn xin nghỉ ngày ${request.date}. Buổi nghỉ này được tính là vắng có phép."
            } else {
                "Giáo viên không duyệt đơn xin nghỉ ngày ${request.date}. Nếu nghỉ, buổi đó bị tính là vắng không phép."
            },
            createdAt = now
        )
    }

    // One reminder for the next occurrence of every weekly lesson, REMINDER_MINUTES before it
    // starts. A lesson that starts sooner than that gets none: the moment has already passed.
    fun classReminders(classes: List<CourseClass>, now: Calendar): List<ClassReminder> =
        ScheduleUtils.upcomingLessons(classes, now).mapNotNull { lesson ->
            val at = lesson.startMillis - REMINDER_MINUTES * 60_000L
            if (at <= now.timeInMillis) return@mapNotNull null
            // The room is free text: some classes hold "101", others "Phòng 101"
            val room = lesson.courseClass.room.trim()
            val where = when {
                room.isEmpty() -> ""
                room.startsWith("Phòng", ignoreCase = true) -> " tại $room"
                else -> " tại phòng $room"
            }
            ClassReminder(
                atMillis = at,
                title = "Sắp đến giờ lên lớp • ${lesson.courseClass.name}",
                body = "Bắt đầu lúc ${lesson.slot.substringBefore('-')}$where, còn $REMINDER_MINUTES phút nữa."
            )
        }

    // When a weekly reminder that went off at atMillis is due again: the same time a week later,
    // or the first such time still ahead if the phone was off for longer than that. Counted in
    // calendar weeks, so a change of daylight saving time keeps the time of day.
    fun nextWeek(atMillis: Long, nowMillis: Long): Long {
        val next = Calendar.getInstance().apply { timeInMillis = atMillis }
        do {
            next.add(Calendar.WEEK_OF_YEAR, 1)
        } while (next.timeInMillis <= nowMillis)
        return next.timeInMillis
    }

    fun newestFirst(notifications: List<AppNotification>): List<AppNotification> =
        notifications.sortedWith(compareByDescending<AppNotification> { it.createdAt }.thenBy { it.id })

    // What the phone should announce now: unread notifications it has not announced before,
    // oldest first. `announcedIds` are the ids remembered from the previous call.
    fun toAnnounce(notifications: List<AppNotification>, announcedIds: Set<String>): List<AppNotification> =
        notifications
            .filter { !it.read && it.id !in announcedIds }
            .sortedBy { it.createdAt }

    // What to do with the status bar after the user's notifications have changed.
    //
    // announcedIds: what the phone has already dealt with (remembered across restarts).
    // canNotify: whether Android lets the app show notifications at all. While it does not, nothing
    // new is marked as dealt with, so it still shows once the user allows notifications.
    fun planAnnouncements(
        notifications: List<AppNotification>,
        announcedIds: Set<String>,
        canNotify: Boolean
    ): AnnouncementPlan {
        val current = notifications.associateBy { it.id }
        // Gone from the database (a corrected absence, a cleared inbox) or read in the app: what is
        // still in the status bar about it is out of date
        val withdraw = announcedIds.filter { id -> current[id]?.read != false }.toSet()
        if (!canNotify) {
            return AnnouncementPlan(emptyList(), withdraw, announcedIds intersect current.keys)
        }
        return AnnouncementPlan(toAnnounce(notifications, announcedIds), withdraw, current.keys)
    }

    // "10:05" for today, "13/10 10:05" for another day
    fun timeLabel(createdAt: Long, now: Long): String {
        val then = Calendar.getInstance().apply { timeInMillis = createdAt }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
        return SimpleDateFormat(pattern, Locale.US).format(Date(createdAt))
    }
}
