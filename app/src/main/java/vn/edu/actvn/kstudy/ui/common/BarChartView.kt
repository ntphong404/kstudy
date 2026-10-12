package vn.edu.actvn.kstudy.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.ChartMath

// Vertical bar chart for values on a 0..100% scale (attendance rate per session)
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Bar(val label: String, val percent: Int)

    private var bars: List<Bar> = emptyList()
    private var progress: Float = 1f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density
    private val barRect = RectF()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_container)
        strokeWidth = density
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline)
        textSize = sp(10f)
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = sp(13f)
        textAlign = Paint.Align.CENTER
    }

    private val goodColor = ContextCompat.getColor(context, R.color.present)
    private val mediumColor = ContextCompat.getColor(context, R.color.late)
    private val badColor = ContextCompat.getColor(context, R.color.absent)

    fun setBars(bars: List<Bar>) {
        this.bars = bars

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (bars.isEmpty()) {
            canvas.drawText("Chưa có buổi điểm danh nào đã chốt", width / 2f, height / 2f, emptyTextPaint)
            return
        }

        val textHeight = axisTextPaint.textSize
        val plotLeft = paddingLeft + axisTextPaint.measureText("100%") + 6f * density
        val plotRight = width - paddingRight.toFloat()
        val plotTop = paddingTop + textHeight + 6f * density // room for the value above a 100% bar
        val plotBottom = height - paddingBottom - textHeight - 8f * density // room for the date labels
        val plotHeight = plotBottom - plotTop
        val plotWidth = plotRight - plotLeft
        if (plotHeight <= 0f || plotWidth <= 0f) return

        axisTextPaint.textAlign = Paint.Align.RIGHT
        for (tick in intArrayOf(0, 50, 100)) {
            val y = plotBottom - plotHeight * ChartMath.barFraction(tick)
            canvas.drawLine(plotLeft, y, plotRight, y, gridPaint)
            canvas.drawText("$tick%", plotLeft - 6f * density, y + textHeight / 3f, axisTextPaint)
        }

        val slot = plotWidth / bars.size
        val barWidth = (slot * 0.55f).coerceAtMost(36f * density)
        val corner = 4f * density
        val maxLabels = (plotWidth / (axisTextPaint.measureText("00/00") + 8f * density)).toInt()
        val labelStep = ChartMath.labelStep(bars.size, maxLabels)
        val showValues = slot >= valueTextPaint.measureText("100%") + 2f * density

        axisTextPaint.textAlign = Paint.Align.CENTER
        bars.forEachIndexed { index, bar ->
            val centerX = plotLeft + slot * index + slot / 2f
            val barHeight = plotHeight * ChartMath.barFraction(bar.percent) * progress
            val top = plotBottom - barHeight

            barPaint.color = when {
                bar.percent >= 80 -> goodColor
                bar.percent >= 50 -> mediumColor
                else -> badColor
            }
            barRect.set(centerX - barWidth / 2f, top, centerX + barWidth / 2f, plotBottom)
            canvas.drawRoundRect(barRect, corner, corner, barPaint)
            // Square off the bottom corners so the bar stands on the axis
            if (barHeight > corner) {
                canvas.drawRect(barRect.left, plotBottom - corner, barRect.right, plotBottom, barPaint)
            }

            if (showValues) {
                canvas.drawText("${bar.percent}%", centerX, top - 4f * density, valueTextPaint)
            }
            if (index % labelStep == 0) {
                canvas.drawText(bar.label, centerX, plotBottom + textHeight + 4f * density, axisTextPaint)
            }
        }
    }

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
