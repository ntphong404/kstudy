package vn.edu.actvn.kstudy.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.RiskLevel
import vn.edu.actvn.kstudy.util.StatusCount

// Small pieces of attendance UI shared by the student, teacher and admin screens
object AttendanceViews {

    // Fills a donut chart and the legend from view_attendance_legend.xml next to it
    fun bindChart(chart: DonutChartView, legend: View, count: StatusCount) {
        val context = chart.context
        chart.setData(
            slices = listOf(
                DonutChartView.Slice(count.present.toFloat(), ContextCompat.getColor(context, R.color.present)),
                DonutChartView.Slice(count.late.toFloat(), ContextCompat.getColor(context, R.color.late)),
                DonutChartView.Slice(count.absent.toFloat(), ContextCompat.getColor(context, R.color.absent))
            ),
            centerText = count.attendanceRate?.let { "$it%" } ?: "--",
            centerLabel = "đi học"
        )
        legend.findViewById<TextView>(R.id.tvLegendPresent).text = count.present.toString()
        legend.findViewById<TextView>(R.id.tvLegendLate).text = count.late.toString()
        legend.findViewById<TextView>(R.id.tvLegendAbsent).text = count.absent.toString()
    }

    // Adds a red (danger) or yellow (warning) card to the container
    fun addWarning(container: ViewGroup, risk: RiskLevel, title: String, body: String) {
        val context = container.context
        val item = LayoutInflater.from(context).inflate(R.layout.item_attendance_warning, container, false)
        val isDanger = risk == RiskLevel.DANGER
        item.setBackgroundResource(if (isDanger) R.drawable.bg_warning_danger else R.drawable.bg_warning_caution)
        item.findViewById<ImageView>(R.id.ivWarningIcon).imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, if (isDanger) R.color.absent else R.color.late)
        )
        item.findViewById<TextView>(R.id.tvWarningTitle).text = title
        item.findViewById<TextView>(R.id.tvWarningBody).text = body
        container.addView(item)
    }
}
