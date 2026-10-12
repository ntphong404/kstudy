package vn.edu.actvn.kstudy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {

    private val delta = 0.001f

    @Test
    fun sweepAngles_splitTheCircleProportionally() {
        val angles = ChartMath.sweepAngles(listOf(10f, 2f, 3f))

        assertEquals(240f, angles[0], delta)
        assertEquals(48f, angles[1], delta)
        assertEquals(72f, angles[2], delta)
        assertEquals(360f, angles.sum(), delta)
    }

    @Test
    fun sweepAngles_singleValue_takesTheWholeCircle() {
        assertEquals(listOf(0f, 360f, 0f), ChartMath.sweepAngles(listOf(0f, 7f, 0f)))
    }

    @Test
    fun sweepAngles_allZero_drawsNothing() {
        assertEquals(listOf(0f, 0f, 0f), ChartMath.sweepAngles(listOf(0f, 0f, 0f)))
    }

    @Test
    fun sweepAngles_emptyInput_isEmpty() {
        assertEquals(emptyList<Float>(), ChartMath.sweepAngles(emptyList()))
    }

    @Test
    fun sweepAngles_negativeValuesCountAsZero() {
        val angles = ChartMath.sweepAngles(listOf(-5f, 1f, 1f))

        assertEquals(0f, angles[0], delta)
        assertEquals(180f, angles[1], delta)
        assertEquals(180f, angles[2], delta)
    }

    @Test
    fun barFraction_mapsPercentToZeroOne() {
        assertEquals(0f, ChartMath.barFraction(0), delta)
        assertEquals(0.67f, ChartMath.barFraction(67), delta)
        assertEquals(1f, ChartMath.barFraction(100), delta)
    }

    @Test
    fun barFraction_clampsOutOfRangeValues() {
        assertEquals(0f, ChartMath.barFraction(-20), delta)
        assertEquals(1f, ChartMath.barFraction(140), delta)
    }

    @Test
    fun labelStep_showsEveryLabelWhenTheyFit() {
        assertEquals(1, ChartMath.labelStep(barCount = 5, maxLabels = 6))
        assertEquals(1, ChartMath.labelStep(barCount = 6, maxLabels = 6))
    }

    @Test
    fun labelStep_skipsLabelsWhenThereAreTooManyBars() {
        assertEquals(2, ChartMath.labelStep(barCount = 7, maxLabels = 6))
        assertEquals(2, ChartMath.labelStep(barCount = 12, maxLabels = 6))
        assertEquals(3, ChartMath.labelStep(barCount = 13, maxLabels = 6))
    }

    @Test
    fun labelStep_degenerateInput_isOne() {
        assertEquals(1, ChartMath.labelStep(barCount = 0, maxLabels = 6))
        assertEquals(1, ChartMath.labelStep(barCount = 5, maxLabels = 0))
    }
}
