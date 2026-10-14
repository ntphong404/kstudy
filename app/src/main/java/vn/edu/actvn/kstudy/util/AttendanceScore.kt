package vn.edu.actvn.kstudy.util

import java.math.BigDecimal
import java.math.RoundingMode

// The attendance score ("điểm chuyên cần") of a student in a class: everybody starts with 10
// points and loses the class's penalty for every absence without permission. Being late and
// being absent with an approved leave request cost nothing.
object AttendanceScore {

    const val MAX = 10.0
    const val DEFAULT_PENALTY = 1.0

    const val INVALID_PENALTY = "Nhập một số từ 0 đến 10, ví dụ 1 hoặc 0,5"

    fun of(count: StatusCount, penalty: Double): Double =
        round(MAX - penalty * count.absent).coerceIn(0.0, MAX)

    // "10", "8,5": a comma as the decimal mark, the way scores are written in Vietnam
    fun format(score: Double): String =
        BigDecimal(score).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    // e.g. "9/10"
    fun outOfTen(score: Double): String = "${format(score)}/${format(MAX)}"

    // e.g. "Mỗi buổi vắng không phép trừ 0,5 điểm"
    fun rule(penalty: Double): String = "Mỗi buổi vắng không phép trừ ${format(penalty)} điểm"

    // The same rule in a few words, for a line that already says what kind of absence counts:
    // e.g. "trừ 0,5 điểm/buổi"
    fun shortRule(penalty: Double): String = "trừ ${format(penalty)} điểm/buổi"

    // What a teacher typed as the penalty for one absence; null when it is not a number from 0 to 10
    fun parsePenalty(text: String): Double? {
        val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value.isNaN() || value < 0.0 || value > MAX) return null
        return round(value)
    }

    // Two decimals are plenty for a score and keep 3 x 0.1 from showing as 0.30000000000000004
    private fun round(value: Double): Double = Math.round(value * 100.0) / 100.0
}
