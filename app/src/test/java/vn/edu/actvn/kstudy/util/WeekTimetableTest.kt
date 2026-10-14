package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.CourseClass
import java.util.Calendar

class WeekTimetableTest {

    private fun courseClass(name: String, schedule: String) =
        CourseClass(id = name, name = name, room = "Phòng 101", schedule = schedule)

    private fun day(year: Int, month: Int, dayOfMonth: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 10, 0)
        }

    // 14/10/2026 is a Wednesday
    private val wednesday = day(2026, Calendar.OCTOBER, 14)

    @Test
    fun weekTimetable_hasOneRowPerDayFromMondayToSunday() {
        val timetable = ScheduleUtils.weekTimetable(emptyList(), wednesday)

        assertEquals(
            listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật"),
            timetable.days.map { it.name }
        )
    }

    @Test
    fun weekTimetable_datesTheWeekThatContainsToday() {
        val timetable = ScheduleUtils.weekTimetable(emptyList(), wednesday)

        assertEquals(
            listOf("12/10", "13/10", "14/10", "15/10", "16/10", "17/10", "18/10"),
            timetable.days.map { it.date }
        )
        assertEquals(listOf("Thứ 4"), timetable.days.filter { it.isToday }.map { it.name })
    }

    @Test
    fun weekTimetable_onSunday_stillShowsTheWeekThatStartedOnMonday() {
        val timetable = ScheduleUtils.weekTimetable(emptyList(), day(2026, Calendar.OCTOBER, 18))

        assertEquals("12/10", timetable.days.first().date)
        assertEquals("18/10", timetable.days.last().date)
        assertTrue(timetable.days.last().isToday)
    }

    @Test
    fun weekTimetable_weekAcrossTwoMonths_keepsTheRightDates() {
        // 01/10/2026 is a Thursday
        val timetable = ScheduleUtils.weekTimetable(emptyList(), day(2026, Calendar.OCTOBER, 1))

        assertEquals(
            listOf("28/09", "29/09", "30/09", "01/10", "02/10", "03/10", "04/10"),
            timetable.days.map { it.date }
        )
    }

    @Test
    fun weekTimetable_putsAClassOnEachOfItsDays() {
        val maths = courseClass("Toán cao cấp A1", "Thứ 2, Thứ 4 (07:30-09:00)")

        val timetable = ScheduleUtils.weekTimetable(listOf(maths), wednesday)

        val daysWithLessons = timetable.days.filter { it.lessons.isNotEmpty() }
        assertEquals(listOf("Thứ 2", "Thứ 4"), daysWithLessons.map { it.name })
        assertEquals(listOf(Lesson(maths, "07:30-09:00")), daysWithLessons[0].lessonsAt("07:30-09:00"))
        assertTrue(daysWithLessons[0].lessonsAt("09:30-11:00").isEmpty())
    }

    @Test
    fun weekTimetable_listsOnlyTheSlotsInUse_earliestFirst() {
        val afternoon = courseClass("Anh văn B1", "Thứ 5 (13:30-15:30)")
        val morning = courseClass("Toán cao cấp A1", "Thứ 2 (07:30-09:00)")

        val timetable = ScheduleUtils.weekTimetable(listOf(afternoon, morning), wednesday)

        assertEquals(listOf("07:30-09:00", "13:30-15:30"), timetable.slots)
        assertFalse(timetable.isEmpty)
    }

    @Test
    fun weekTimetable_sundayClass_isOnTheLastRowOnly() {
        // "Chủ Nhật" must not be mistaken for another day and no other day for it
        val sunday = courseClass("Bóng đá", "Chủ Nhật (15:30-17:00)")

        val timetable = ScheduleUtils.weekTimetable(listOf(sunday), wednesday)

        assertEquals(listOf("Chủ Nhật"), timetable.days.filter { it.lessons.isNotEmpty() }.map { it.name })
    }

    @Test
    fun weekTimetable_twoClassesAtTheSameTime_areBothKept() {
        val b = courseClass("Vật lý", "Thứ 3 (09:30-11:00)")
        val a = courseClass("Hóa học", "Thứ 3 (09:30-11:00)")

        val timetable = ScheduleUtils.weekTimetable(listOf(b, a), wednesday)

        val tuesday = timetable.days[1]
        assertEquals(listOf("Hóa học", "Vật lý"), tuesday.lessonsAt("09:30-11:00").map { it.courseClass.name })
        assertEquals(listOf("09:30-11:00"), timetable.slots)
    }

    @Test
    fun weekTimetable_scheduleWithoutAKnownDayOrSlot_isLeftOut() {
        val classes = listOf(
            courseClass("Chưa xếp lịch", ""),
            courseClass("Thiếu giờ", "Thứ 2"),
            courseClass("Giờ lạ", "Thứ 2 (18:00-20:00)"),
            courseClass("Thiếu ngày", "(07:30-09:00)")
        )

        val timetable = ScheduleUtils.weekTimetable(classes, wednesday)

        assertTrue(timetable.isEmpty)
        assertTrue(timetable.days.all { it.lessons.isEmpty() })
        assertEquals("the week is still shown", 7, timetable.days.size)
    }
}
