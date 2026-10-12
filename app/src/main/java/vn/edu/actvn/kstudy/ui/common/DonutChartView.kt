package vn.edu.actvn.kstudy.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.ChartMath

// Ring chart drawn directly on the Canvas, with a headline value in the middle
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Slice(val value: Float, val color: Int)

    private var slices: List<Slice> = emptyList()
    private var sweeps: List<Float> = emptyList()
    private var centerText: String = "--"
    private var centerLabel: String = ""

    // 0..1, animated so the ring is drawn progressively
    private var progress: Float = 1f
    private var animator: ValueAnimator? = null

    private val oval = RectF()
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        color = ContextCompat.getColor(context, R.color.black)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }
    private val trackColor = ContextCompat.getColor(context, R.color.surface_container)

    fun setData(slices: List<Slice>, centerText: String, centerLabel: String = "") {
        this.slices = slices
        this.sweeps = ChartMath.sweepAngles(slices.map { it.value })
        this.centerText = centerText
        this.centerLabel = centerLabel

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

        val size = (width - paddingLeft - paddingRight)
            .coerceAtMost(height - paddingTop - paddingBottom)
            .toFloat()
        if (size <= 0f) return

        val stroke = size * 0.15f
        val radius = (size - stroke) / 2f
        val cx = paddingLeft + (width - paddingLeft - paddingRight) / 2f
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        ringPaint.strokeWidth = stroke

        ringPaint.color = trackColor
        canvas.drawArc(oval, 0f, 360f, false, ringPaint)

        // Start at 12 o'clock and go clockwise
        var startAngle = -90f
        for (i in slices.indices) {
            val sweep = sweeps[i] * progress
            if (sweep > 0f) {
                ringPaint.color = slices[i].color
                canvas.drawArc(oval, startAngle, sweep, false, ringPaint)
            }
            startAngle += sweep
        }

        textPaint.textSize = size * 0.22f
        labelPaint.textSize = size * 0.10f
        if (centerLabel.isEmpty()) {
            canvas.drawText(centerText, cx, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        } else {
            canvas.drawText(centerText, cx, cy, textPaint)
            canvas.drawText(centerLabel, cx, cy + labelPaint.textSize * 1.5f, labelPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
