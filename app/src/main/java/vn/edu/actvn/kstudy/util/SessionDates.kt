package vn.edu.actvn.kstudy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// The `date` field of a session is a string: "dd/MM/yyyy HH:mm" (older sessions only have "dd/MM/yyyy")
object SessionDates {

    private const val WITH_TIME = "dd/MM/yyyy HH:mm"
    private const val DATE_ONLY = "dd/MM/yyyy"
    private const val DAY_MONTH = "dd/MM"

    // Returns 0 when the string cannot be parsed, so such sessions sort first
    fun toMillis(dateStr: String): Long = parseOrNull(dateStr) ?: 0L

    // "01/07/2026 07:35" -> "01/07/2026"
    fun dateOnly(dateStr: String): String = reformat(dateStr, DATE_ONLY)

    // "01/07/2026 07:35" -> "01/07", short enough for a chart axis
    fun dayMonth(dateStr: String): String = reformat(dateStr, DAY_MONTH)

    private fun reformat(dateStr: String, pattern: String): String {
        val millis = parseOrNull(dateStr) ?: return dateStr.trim()
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    }

    private fun parseOrNull(dateStr: String): Long? {
        return parse(WITH_TIME, dateStr) ?: parse(DATE_ONLY, dateStr)
    }

    private fun parse(pattern: String, dateStr: String): Long? {
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).parse(dateStr.trim())?.time
        } catch (e: Exception) {
            null
        }
    }
}
