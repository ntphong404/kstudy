package vn.edu.actvn.kstudy.util

import vn.edu.actvn.kstudy.data.model.CourseClass
import java.util.Calendar

data class UpcomingClass(
    val courseClass: CourseClass,
    val dayLabel: String,
    val timeLabel: String
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
