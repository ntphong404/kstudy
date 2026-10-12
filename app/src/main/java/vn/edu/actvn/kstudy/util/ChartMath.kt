package vn.edu.actvn.kstudy.util

// Geometry shared by the chart views, kept free of Android classes so it can be unit tested
object ChartMath {

    // Splits a full circle between the values, proportionally. Negative values count as 0;
    // when there is nothing to show every slice gets 0 degrees.
    fun sweepAngles(values: List<Float>): List<Float> {
        val safe = values.map { it.coerceAtLeast(0f) }
        val total = safe.sum()
        if (total <= 0f) return safe.map { 0f }
        return safe.map { it / total * 360f }
    }

    // Height of a bar as a fraction of the plot area, for a value on a 0..100 scale
    fun barFraction(percent: Int): Float = percent.coerceIn(0, 100) / 100f

    // Draw only every n-th axis label so that at most `maxLabels` are shown
    fun labelStep(barCount: Int, maxLabels: Int): Int {
        if (barCount <= 0 || maxLabels <= 0) return 1
        return (barCount + maxLabels - 1) / maxLabels
    }
}
