package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.CourseClass
import java.text.ParseException
import java.text.SimpleDateFormat
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
    val weekday: Int, // column of the day in a week that starts on Monday: 0 = Thứ 2 ... 6 = Chủ Nhật
    val year: Int,
    val month: Int, // 1 = January
    val dayOfMonth: Int,
    val isToday: Boolean,
    val lessons: List<Lesson>
) {
    // "dd/MM"
    val date: String get() = String.format(Locale.US, "%02d/%02d", dayOfMonth, month)

    // "dd/MM/yyyy"
    val fullDate: String get() = String.format(Locale.US, "%02d/%02d/%04d", dayOfMonth, month, year)

    fun lessonsAt(slot: String): List<Lesson> = lessons.filter { it.slot == slot }
}

enum class TimetableMode { DAY, WEEK, MONTH }

// The days of one period: a single day, Monday to Sunday of a week, or the first to the last day
// of a month. `selected` is the day the period was built around: the day shown alone, or the one
// picked in the month.
// `slots` holds every time slot the user's classes use, earliest first, whether or not one falls in
// this period: the week grid keeps the same columns from one week to the next.
data class Timetable(
    val mode: TimetableMode,
    val slots: List<String>,
    val days: List<TimetableDay>,
    val selected: TimetableDay
) {
    // None of the user's classes can be placed, in any period
    val isEmpty: Boolean get() = slots.isEmpty()

    val title: String
        get() = when (mode) {
            TimetableMode.DAY -> "${selected.name}, ${selected.fullDate}"
            TimetableMode.WEEK -> "Tuần ${days.first().date} – ${days.last().fullDate}"
            TimetableMode.MONTH -> "Tháng ${selected.month}/${selected.year}"
        }
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

    // Lays the classes out over the week that contains `today`
    fun weekTimetable(classes: List<CourseClass>, today: Calendar = Calendar.getInstance()): Timetable =
        timetable(classes, TimetableMode.WEEK, today, today)

    // Lays the classes out over the period of `mode` that contains `day`. A class whose schedule
    // names no known day or time slot cannot be placed and is left out; one with a start or an end
    // date only meets from the one and up to the other.
    fun timetable(
        classes: List<CourseClass>,
        mode: TimetableMode,
        day: Calendar,
        today: Calendar = Calendar.getInstance()
    ): Timetable {
        val placements = classes.mapNotNull { placement(it) }
        val cursor = day.clone() as Calendar
        val count = when (mode) {
            TimetableMode.DAY -> 1
            TimetableMode.WEEK -> {
                cursor.add(Calendar.DAY_OF_YEAR, -weekdayOf(day))
                7
            }
            TimetableMode.MONTH -> {
                cursor.set(Calendar.DAY_OF_MONTH, 1)
                day.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
        }

        val todayKey = dateKey(today)
        val dayKey = dateKey(day)
        var selected: TimetableDay? = null
        val days = List(count) {
            val key = dateKey(cursor)
            val weekday = weekdayOf(cursor)
            val dayName = dayNameToCalendarDay.keys.elementAt(weekday)
            val lessons = placements
                .filter { it.meetsOn(dayName, key) }
                .map { Lesson(it.courseClass, it.slot) }
                .sortedWith(compareBy({ startMinutes(it.slot) }, { it.courseClass.name }))
            val timetableDay = TimetableDay(
                name = dayName,
                weekday = weekday,
                year = cursor.get(Calendar.YEAR),
                month = cursor.get(Calendar.MONTH) + 1,
                dayOfMonth = cursor.get(Calendar.DAY_OF_MONTH),
                isToday = key == todayKey,
                lessons = lessons
            )
            if (key == dayKey) selected = timetableDay
            cursor.add(Calendar.DAY_OF_YEAR, 1)
            timetableDay
        }

        val slots = placements.map { it.slot }.distinct().sortedBy { startMinutes(it) }
        return Timetable(mode, slots, days, selected ?: days.first())
    }

    // The dates a class meets between, for its details: "01/09/2026 – 15/01/2027", "Từ 01/09/2026",
    // "Đến 15/01/2027", or "" for a class without dates
    fun datesLabel(courseClass: CourseClass): String {
        val start = courseClass.startDate.trim()
        val end = courseClass.endDate.trim()
        return when {
            start.isNotEmpty() && end.isNotEmpty() -> "$start – $end"
            start.isNotEmpty() -> "Từ $start"
            end.isNotEmpty() -> "Đến $end"
            else -> ""
        }
    }

    // The dates of a class can be saved unless it would end before it starts; either may be blank
    fun isValidDateRange(startDate: String, endDate: String): Boolean {
        val first = dateKey(startDate) ?: return true
        val last = dateKey(endDate) ?: return true
        return first <= last
    }

    const val DATE_PATTERN = "dd/MM/yyyy"
    const val END_BEFORE_START = "Ngày kết thúc không được trước ngày bắt đầu"

    // A class that has a place on the timetable: its time slot and the first and last day it meets,
    // as date keys, null when it has no such date
    private class Placement(val courseClass: CourseClass, val slot: String, val firstDay: Int?, val lastDay: Int?) {
        fun meetsOn(dayName: String, dayKey: Int): Boolean =
            courseClass.schedule.contains(dayName) &&
                (firstDay == null || dayKey >= firstDay) &&
                (lastDay == null || dayKey <= lastDay)
    }

    private fun placement(courseClass: CourseClass): Placement? {
        val schedule = courseClass.schedule
        val slot = timeSlotRange.keys.firstOrNull { schedule.contains(it) } ?: return null
        if (dayNameToCalendarDay.keys.none { schedule.contains(it) }) return null
        return Placement(courseClass, slot, dateKey(courseClass.startDate), dateKey(courseClass.endDate))
    }

    // Calendar counts from Sunday; the school week starts on Monday
    private fun weekdayOf(day: Calendar): Int = (day.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7

    // A day as yyyyMMdd, so that two days compare as numbers whatever their time of day
    private fun dateKey(day: Calendar): Int =
        day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100 + day.get(Calendar.DAY_OF_MONTH)

    // null for a blank date or one that is not "dd/MM/yyyy"
    private fun dateKey(date: String): Int? {
        if (date.isBlank()) return null
        val format = SimpleDateFormat(DATE_PATTERN, Locale.US).apply { isLenient = false }
        val parsed = try {
            format.parse(date.trim())
        } catch (e: ParseException) {
            null
        } ?: return null
        return dateKey(Calendar.getInstance().apply { time = parsed })
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
