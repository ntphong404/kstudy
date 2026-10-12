package vn.edu.actvn.kstudy.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.common.AttendanceViews
import vn.edu.actvn.kstudy.ui.common.DonutChartView
import vn.edu.actvn.kstudy.util.AttendanceAnalyzer
import vn.edu.actvn.kstudy.util.RiskLevel
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.util.StatusCount
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import vn.edu.actvn.kstudy.viewmodel.StatsViewModel
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel

class StudentDashboardFragment : Fragment() {

    private var studentUserId: String = ""
    private lateinit var viewModel: StudentViewModel
    private lateinit var statsViewModel: StatsViewModel

    companion object {
        fun newInstance(userId: String): StudentDashboardFragment {
            return StudentDashboardFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            studentUserId = it.getString("userId") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_student_dashboard, container, false)

        viewModel = ViewModelProvider(this)[StudentViewModel::class.java]
        statsViewModel = ViewModelProvider(this, KstudyViewModelFactory)[StatsViewModel::class.java]

        val tvWelcome = view.findViewById<TextView>(R.id.tvStudentDashboardWelcome)
        val tvDetails = view.findViewById<TextView>(R.id.tvStudentDashboardSubDetails)
        val tvStatClasses = view.findViewById<TextView>(R.id.tvStudentStatClasses)
        val tvStatAttendance = view.findViewById<TextView>(R.id.tvStudentStatAttendance)
        val tvUpcomingName = view.findViewById<TextView>(R.id.tvStudentUpcomingClassName)
        val tvUpcomingInfo = view.findViewById<TextView>(R.id.tvStudentUpcomingClassInfo)
        val layoutWarnings = view.findViewById<LinearLayout>(R.id.layoutStudentWarnings)
        val chartAttendance = view.findViewById<DonutChartView>(R.id.chartStudentAttendance)
        val legendAttendance = view.findViewById<View>(R.id.legendStudentAttendance)
        val tvChartNote = view.findViewById<TextView>(R.id.tvStudentChartNote)

        viewModel.student.observe(viewLifecycleOwner) { student ->
            if (student != null) {
                tvWelcome.text = "Chào học sinh, ${student.name}"
                tvDetails.text = "Mã số: ${student.studentCode} • Lớp chuyên ngành: ${student.className}"
            }
        }

        viewModel.classes.observe(viewLifecycleOwner) { classes ->
            tvStatClasses.text = classes.size.toString()

            val upcoming = ScheduleUtils.findUpcomingClass(classes)
            if (upcoming != null) {
                tvUpcomingName.text = upcoming.courseClass.name
                tvUpcomingInfo.text =
                    "${upcoming.dayLabel} • ${upcoming.timeLabel} • Phòng ${upcoming.courseClass.room}"
            } else {
                tvUpcomingName.text = "Chưa có lịch học"
                tvUpcomingInfo.text = "Bạn chưa được xếp vào lớp học nào có lịch cụ thể."
            }
        }

        // The rate card, the chart and the warnings all come from the same statistics,
        // so the numbers on this screen always agree with each other.
        statsViewModel.studentStats.observe(viewLifecycleOwner) { stats ->
            val total = stats.fold(StatusCount()) { acc, stat -> acc + stat.count }
            tvStatAttendance.text = total.attendanceRate?.let { "$it%" } ?: "--"

            AttendanceViews.bindChart(chartAttendance, legendAttendance, total)
            tvChartNote.text = if (total.total > 0) {
                "Tính trên ${total.total} buổi điểm danh đã chốt."
            } else {
                "Chưa có buổi điểm danh nào đã chốt."
            }

            layoutWarnings.removeAllViews()
            for (stat in stats.filter { it.risk != RiskLevel.SAFE }.sortedByDescending { it.risk }) {
                AttendanceViews.addWarning(
                    container = layoutWarnings,
                    risk = stat.risk,
                    title = "${AttendanceAnalyzer.riskLabel(stat.risk)} • ${stat.courseClass.name}",
                    body = AttendanceAnalyzer.studentWarning(stat.count, stat.risk)
                )
            }
            layoutWarnings.visibility = if (layoutWarnings.childCount > 0) View.VISIBLE else View.GONE
        }

        statsViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                tvChartNote.text = message
                statsViewModel.clearErrorMessage()
            }
        }

        viewModel.loadStudent(studentUserId)
        statsViewModel.loadStudentStats(studentUserId)

        return view
    }
}
