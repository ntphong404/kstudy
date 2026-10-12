package vn.edu.actvn.kstudy.ui.common

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus
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
                DonutChartView.Slice(count.absent.toFloat(), ContextCompat.getColor(context, R.color.absent)),
                DonutChartView.Slice(count.excused.toFloat(), ContextCompat.getColor(context, R.color.excused))
            ),
            centerText = count.attendanceRate?.let { "$it%" } ?: "--",
            centerLabel = "đi học"
        )
        legend.findViewById<TextView>(R.id.tvLegendPresent).text = count.present.toString()
        legend.findViewById<TextView>(R.id.tvLegendLate).text = count.late.toString()
        legend.findViewById<TextView>(R.id.tvLegendAbsent).text = count.absent.toString()
        legend.findViewById<TextView>(R.id.tvLegendExcused).text = count.excused.toString()
    }

    // Adds one leave request card. The teacher sees who is asking and, while the request is
    // pending, the two buttons; onDecide receives true for "approve".
    fun addLeaveRequest(
        container: ViewGroup,
        request: LeaveRequest,
        forTeacher: Boolean,
        onDecide: ((Boolean) -> Unit)? = null
    ) {
        val context = container.context
        val item = LayoutInflater.from(context).inflate(R.layout.item_leave_request, container, false)
        val tvDate = item.findViewById<TextView>(R.id.tvLeaveDate)
        if (forTeacher) {
            item.findViewById<TextView>(R.id.tvLeaveTitle).text = "${request.studentName} (${request.studentCode})"
            tvDate.text = "Xin nghỉ ngày ${request.date}"
        } else {
            item.findViewById<TextView>(R.id.tvLeaveTitle).text = "Ngày ${request.date}"
            tvDate.visibility = View.GONE
        }
        item.findViewById<TextView>(R.id.tvLeaveReason).text = "Lý do: ${request.reason}"

        val (textColor, chipColor) = when (request.status) {
            LeaveStatus.APPROVED -> R.color.present to R.color.present_bg
            LeaveStatus.REJECTED -> R.color.absent to R.color.absent_bg
            else -> R.color.on_primary_container to R.color.primary_container
        }
        item.findViewById<TextView>(R.id.tvLeaveStatus).apply {
            text = LeaveStatus.label(request.status)
            setTextColor(ContextCompat.getColor(context, textColor))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, chipColor))
        }

        if (onDecide != null && request.status == LeaveStatus.PENDING) {
            item.findViewById<View>(R.id.layoutLeaveActions).visibility = View.VISIBLE
            item.findViewById<View>(R.id.btnLeaveApprove).setOnClickListener { onDecide(true) }
            item.findViewById<View>(R.id.btnLeaveReject).setOnClickListener { onDecide(false) }
        }
        container.addView(item)
    }

    // Placeholder line of an empty list
    fun addEmptyNote(container: ViewGroup, message: String) {
        val context = container.context
        container.addView(
            TextView(context).apply {
                text = message
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
            }
        )
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
