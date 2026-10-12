package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDatesTest {

    @Test
    fun toMillis_ordersDatesChronologically() {
        val earlier = SessionDates.toMillis("30/09/2026 07:30")
        val later = SessionDates.toMillis("01/10/2026 07:30")

        assertTrue(earlier < later)
    }

    @Test
    fun toMillis_ordersTimesOnTheSameDay() {
        assertTrue(SessionDates.toMillis("01/10/2026 07:30") < SessionDates.toMillis("01/10/2026 13:30"))
    }

    @Test
    fun toMillis_acceptsOldFormatWithoutTime() {
        val dateOnly = SessionDates.toMillis("01/10/2026")

        assertTrue(dateOnly > 0)
        assertTrue(dateOnly < SessionDates.toMillis("01/10/2026 00:01"))
        assertTrue(dateOnly > SessionDates.toMillis("30/09/2026 23:59"))
    }

    @Test
    fun toMillis_unparseableText_isZero() {
        assertEquals(0L, SessionDates.toMillis(""))
        assertEquals(0L, SessionDates.toMillis("hôm qua"))
    }

    @Test
    fun dateOnly_dropsTheTime() {
        assertEquals("01/07/2026", SessionDates.dateOnly("01/07/2026 07:35"))
        assertEquals("01/07/2026", SessionDates.dateOnly("01/07/2026"))
    }

    @Test
    fun dayMonth_keepsDayAndMonthOnly() {
        assertEquals("01/07", SessionDates.dayMonth("01/07/2026 07:35"))
        assertEquals("29/09", SessionDates.dayMonth(" 29/09/2026 "))
    }

    @Test
    fun unparseableText_isReturnedTrimmed() {
        assertEquals("hôm qua", SessionDates.dateOnly(" hôm qua "))
        assertEquals("", SessionDates.dayMonth(""))
    }
}
