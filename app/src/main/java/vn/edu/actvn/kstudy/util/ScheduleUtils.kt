package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.CourseClass
import java.util.Calendar
import java.util.Locale

data class UpcomingClass(
    val courseClass: CourseClass,
    val dayLabel: String,
    val timeLabel: String
)

// One weekly lesson: a class that meets on several days has one lesson on each of them
data class Lesson(
    val courseClass: CourseClass,
    val slot: String // "07:30-09:00"
)

data class TimetableDay(
    val name: String, // "Thứ 2" ... "Chủ Nhật"
    val date: String, // day of the shown week, "dd/MM"
    val isToday: Boolean,
    val lessons: List<Lesson>
) {
    fun lessonsAt(slot: String): List<Lesson> = lessons.filter { it.slot == slot }
}

// The week as a grid: one row per day from Monday to Sunday, one column per time slot that
// at least one class uses, earliest first
data class WeekTimetable(
    val slots: List<String>,
    val days: List<TimetableDay>
) {
    val isEmpty: Boolean get() = slots.isEmpty()
}

// The next occurrence of a weekly lesson
data class UpcomingLesson(
    val courseClass: CourseClass,
    val slot: String, // "07:30-09:00"
    val startMillis: Long
)

// Parses the free-text `schedule` field (e.g. "Thứ 2, Thứ 4 (7h30-9h)") set by
// ManageClassesFragment and checks it against the real device clock.
object ScheduleUtils {

    private val dayNameToCalendarDay = linkedMapOf(
        "Thứ 2" to Calendar.MONDAY,
        "Thứ 3" to Calendar.TUESDAY,
        "Thứ 4" to Calendar.WEDNESDAY,
        "Thứ 5" to Calendar.THURSDAY,
        "Thứ 6" to Calendar.FRIDAY,
        "Thứ 7" to Calendar.SATURDAY,
        "Chủ Nhật" to Calendar.SUNDAY
    )

    // slot label (24h format, unambiguous) -> [startHour, startMinute, endHour, endMinute]
    private val timeSlotRange = linkedMapOf(
        "07:30-09:00" to intArrayOf(7, 30, 9, 0),
        "09:30-11:00" to intArrayOf(9, 30, 11, 0),
        "13:30-15:30" to intArrayOf(13, 30, 15, 30),
        "15:30-17:00" to intArrayOf(15, 30, 17, 0),
        "04:00-05:30" to intArrayOf(4, 0, 5, 30) // TEMP TEST SLOT - remove before submission
    )

    fun findUpcomingClass(classes: List<CourseClass>): UpcomingClass? {
        val now = Calendar.getInstance()
        var best: UpcomingClass? = null
        var bestMillis = Long.MAX_VALUE

        for (courseClass in classes) {
            val schedule = courseClass.schedule
            if (schedule.isBlank()) continue

            val timeSlotKey = timeSlotRange.keys.firstOrNull { schedule.contains(it) } ?: continue
            val (startHour, startMinute, _, _) = timeSlotRange.getValue(timeSlotKey)

            for ((dayName, calendarDay) in dayNameToCalendarDay) {
                if (!schedule.contains(dayName)) continue

                val candidate = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var dayDiff = calendarDay - now.get(Calendar.DAY_OF_WEEK)
                if (dayDiff < 0 || (dayDiff == 0 && candidate.before(now))) {
                    dayDiff += 7
                }
                candidate.add(Calendar.DAY_OF_YEAR, dayDiff)

                if (candidate.timeInMillis < bestMillis) {
                    bestMillis = candidate.timeInMillis
                    val dayLabel = when (dayDiff) {
                        0 -> "Hôm nay"
                        1 -> "Ngày mai"
                        else -> dayName
                    }
                    best = UpcomingClass(courseClass, dayLabel, timeSlotKey)
                }
            }
        }
        return best
    }

    // Lays the classes out over the week that contains `today`. A class whose schedule names no
    // known day or time slot cannot be placed and is left out.
    fun weekTimetable(classes: List<CourseClass>, today: Calendar = Calendar.getInstance()): WeekTimetable {
        val slotOfClass = classes.mapNotNull { courseClass ->
            timeSlotRange.keys.firstOrNull { courseClass.schedule.contains(it) }?.let { courseClass to it }
        }

        // Calendar counts from Sunday; the school week starts on Monday
        val daysSinceMonday = (today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val day = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysSinceMonday) }

        val days = dayNameToCalendarDay.keys.mapIndexed { index, dayName ->
            val lessons = slotOfClass
                .filter { (courseClass, _) -> courseClass.schedule.contains(dayName) }
                .map { (courseClass, slot) -> Lesson(courseClass, slot) }
                .sortedWith(compareBy({ startMinutes(it.slot) }, { it.courseClass.name }))
            val date = String.format(Locale.US, "%02d/%02d", day.get(Calendar.DAY_OF_MONTH), day.get(Calendar.MONTH) + 1)
            day.add(Calendar.DAY_OF_YEAR, 1)
            TimetableDay(dayName, date, isToday = index == daysSinceMonday, lessons = lessons)
        }

        val slots = days.flatMap { it.lessons }.map { it.slot }.distinct().sortedBy { startMinutes(it) }
        return WeekTimetable(slots, days)
    }

    // The next time each weekly lesson starts, counted from `now`, soonest first: a class that
    // meets twice a week gives two lessons. A lesson that has already begun today comes back
    // as next week's.
    fun upcomingLessons(classes: List<CourseClass>, now: Calendar = Calendar.getInstance()): List<UpcomingLesson> {
        val lessons = ArrayList<UpcomingLesson>()
        for (courseClass in classes) {
            val slot = timeSlotRange.keys.firstOrNull { courseClass.schedule.contains(it) } ?: continue
            val (startHour, startMinute, _, _) = timeSlotRange.getValue(slot)
            for ((dayName, calendarDay) in dayNameToCalendarDay) {
                if (!courseClass.schedule.contains(dayName)) continue
                val start = (now.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                var dayDiff = (calendarDay - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (dayDiff == 0 && !start.after(now)) dayDiff = 7
                start.add(Calendar.DAY_OF_YEAR, dayDiff)
                lessons.add(UpcomingLesson(courseClass, slot, start.timeInMillis))
            }
        }
        return lessons.sortedWith(compareBy({ it.startMillis }, { it.courseClass.name }))
    }

    private fun startMinutes(slot: String): Int {
        val (startHour, startMinute, _, _) = timeSlotRange.getValue(slot)
        return startHour * 60 + startMinute
    }

    // True only when the real device clock is on a scheduled day, inside the scheduled time slot.
    fun isWithinScheduledTime(schedule: String): Boolean {
        if (schedule.isBlank()) return false
        val now = Calendar.getInstance()
        val todayName = dayNameToCalendarDay.entries
            .firstOrNull { it.value == now.get(Calendar.DAY_OF_WEEK) }
            ?.key ?: return false
        if (!schedule.contains(todayName)) return false

        val timeSlotKey = timeSlotRange.keys.firstOrNull { schedule.contains(it) } ?: return false
        val (startHour, startMinute, endHour, endMinute) = timeSlotRange.getValue(timeSlotKey)
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        return nowMinutes in startMinutes..endMinutes
    }
}

private operator fun IntArray.component1() = this[0]
private operator fun IntArray.component2() = this[1]
private operator fun IntArray.component3() = this[2]
private operator fun IntArray.component4() = this[3]
