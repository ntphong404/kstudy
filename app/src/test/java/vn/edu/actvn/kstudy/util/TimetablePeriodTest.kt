package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.CourseClass
import java.util.Calendar

class TimetablePeriodTest {

    private fun courseClass(name: String, schedule: String, startDate: String = "", endDate: String = "") =
        CourseClass(id = name, name = name, room = "Phòng 101", schedule = schedule, startDate = startDate, endDate = endDate)

    private fun day(year: Int, month: Int, dayOfMonth: Int): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 10, 0)
        }

    // 14/10/2026 is a Wednesday
    private val wednesday = day(2026, Calendar.OCTOBER, 14)

    private val mondays = courseClass("Toán cao cấp A1", "Thứ 2 (07:30-09:00)")

    private fun daysWithLessons(timetable: Timetable) = timetable.days.filter { it.lessons.isNotEmpty() }.map { it.date }

    @Test
    fun day_holdsOnlyThatDay_withItsLessons() {
        val timetable = ScheduleUtils.timetable(listOf(mondays), TimetableMode.DAY, day(2026, Calendar.OCTOBER, 12), wednesday)

        assertEquals(1, timetable.days.size)
        assertEquals("Thứ 2", timetable.selected.name)
        assertEquals(listOf("Toán cao cấp A1"), timetable.selected.lessons.map { it.courseClass.name })
        assertEquals("Thứ 2, 12/10/2026", timetable.title)
        assertFalse(timetable.selected.isToday)
    }

    @Test
    fun month_holdsEveryDayOfTheMonth_andPutsTheFirstUnderItsWeekday() {
        val timetable = ScheduleUtils.timetable(listOf(mondays), TimetableMode.MONTH, wednesday, wednesday)

        assertEquals(31, timetable.days.size)
        assertEquals("01/10", timetable.days.first().date)
        assertEquals("31/10", timetable.days.last().date)
        // 01/10/2026 is a Thursday: the fourth column
        assertEquals(3, timetable.days.first().weekday)
        assertEquals("Thứ 5", timetable.days.first().name)
        assertEquals("Tháng 10/2026", timetable.title)
    }

    @Test
    fun month_putsAWeeklyClassOnEveryOneOfItsDays() {
        val timetable = ScheduleUtils.timetable(listOf(mondays), TimetableMode.MONTH, wednesday, wednesday)

        assertEquals(listOf("05/10", "12/10", "19/10", "26/10"), daysWithLessons(timetable))
    }

    @Test
    fun month_selectsTheDayItWasBuiltAround_andMarksToday() {
        val timetable = ScheduleUtils.timetable(
            emptyList(), TimetableMode.MONTH, day(2026, Calendar.OCTOBER, 20), wednesday
        )

        assertEquals("20/10/2026", timetable.selected.fullDate)
        assertEquals(listOf("14/10"), timetable.days.filter { it.isToday }.map { it.date })
    }

    @Test
    fun month_leapFebruary_hasTwentyNineDays() {
        val timetable = ScheduleUtils.timetable(emptyList(), TimetableMode.MONTH, day(2028, Calendar.FEBRUARY, 10))

        assertEquals(29, timetable.days.size)
        assertEquals("29/02", timetable.days.last().date)
    }

    @Test
    fun week_ofAnotherDay_isNotTodaysWeek() {
        val nextWeek = day(2026, Calendar.OCTOBER, 21)

        val timetable = ScheduleUtils.timetable(emptyList(), TimetableMode.WEEK, nextWeek, wednesday)

        assertEquals("19/10", timetable.days.first().date)
        assertEquals("25/10", timetable.days.last().date)
        assertTrue(timetable.days.none { it.isToday })
        assertEquals("21/10/2026", timetable.selected.fullDate)
    }

    @Test
    fun week_title_namesTheYearOfItsLastDay() {
        // 31/12/2026 is a Thursday: its week ends in 2027
        val timetable = ScheduleUtils.timetable(emptyList(), TimetableMode.WEEK, day(2026, Calendar.DECEMBER, 31))

        assertEquals("Tuần 28/12 – 03/01/2027", timetable.title)
    }

    @Test
    fun classWithDates_meetsOnlyFromItsStartToItsEnd() {
        // Wednesdays of October 2026: 7, 14, 21, 28
        val shortClass = courseClass("Vật lý", "Thứ 4 (09:30-11:00)", startDate = "07/10/2026", endDate = "21/10/2026")

        val timetable = ScheduleUtils.timetable(listOf(shortClass), TimetableMode.MONTH, wednesday, wednesday)

        assertEquals(listOf("07/10", "14/10", "21/10"), daysWithLessons(timetable))
    }

    @Test
    fun classWithOnlyAStartDate_meetsEveryWeekFromThen() {
        val lateStart = courseClass("Vật lý", "Thứ 4 (09:30-11:00)", startDate = "15/10/2026")

        val timetable = ScheduleUtils.timetable(listOf(lateStart), TimetableMode.MONTH, wednesday, wednesday)

        assertEquals(listOf("21/10", "28/10"), daysWithLessons(timetable))
    }

    @Test
    fun classThatHasEnded_keepsItsColumnInTheWeekGrid() {
        val ended = courseClass("Vật lý", "Thứ 4 (09:30-11:00)", endDate = "21/10/2026")

        val timetable = ScheduleUtils.timetable(listOf(ended), TimetableMode.WEEK, day(2026, Calendar.NOVEMBER, 4), wednesday)

        assertTrue(timetable.days.all { it.lessons.isEmpty() })
        assertEquals(listOf("09:30-11:00"), timetable.slots)
        assertFalse("the user still has a class, in another week", timetable.isEmpty)
    }

    @Test
    fun dateThatCannotBeRead_isIgnored() {
        val odd = courseClass("Vật lý", "Thứ 4 (09:30-11:00)", startDate = "sắp tới", endDate = "31/02/2026")

        val timetable = ScheduleUtils.timetable(listOf(odd), TimetableMode.MONTH, wednesday, wednesday)

        assertEquals(listOf("07/10", "14/10", "21/10", "28/10"), daysWithLessons(timetable))
    }

    @Test
    fun isValidDateRange_onlyRejectsAnEndBeforeTheStart() {
        assertTrue(ScheduleUtils.isValidDateRange("", ""))
        assertTrue(ScheduleUtils.isValidDateRange("01/09/2026", ""))
        assertTrue(ScheduleUtils.isValidDateRange("", "15/01/2027"))
        assertTrue(ScheduleUtils.isValidDateRange("01/09/2026", "15/01/2027"))
        assertTrue("a class of a single day", ScheduleUtils.isValidDateRange("01/09/2026", "01/09/2026"))
        assertFalse(ScheduleUtils.isValidDateRange("15/01/2027", "01/09/2026"))
    }

    @Test
    fun datesLabel_saysWhichDatesTheClassHas() {
        assertEquals("01/09/2026 – 15/01/2027", ScheduleUtils.datesLabel(CourseClass(startDate = "01/09/2026", endDate = "15/01/2027")))
        assertEquals("Từ 01/09/2026", ScheduleUtils.datesLabel(CourseClass(startDate = "01/09/2026")))
        assertEquals("Đến 15/01/2027", ScheduleUtils.datesLabel(CourseClass(endDate = "15/01/2027")))
        assertEquals("", ScheduleUtils.datesLabel(CourseClass()))
    }
}
