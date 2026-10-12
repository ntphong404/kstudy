package vn.edu.actvn.kstudy.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.common.AiAssistantFragment
import vn.edu.actvn.kstudy.ui.common.AttendanceViews
import vn.edu.actvn.kstudy.ui.common.DonutChartView
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import vn.edu.actvn.kstudy.viewmodel.StatsViewModel

class AdminDashboardFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var statsViewModel: StatsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]
        statsViewModel = ViewModelProvider(this, KstudyViewModelFactory)[StatsViewModel::class.java]

        val tvStudents = view.findViewById<TextView>(R.id.tvStatStudents)
        val tvClasses = view.findViewById<TextView>(R.id.tvStatClasses)
        val tvTeachers = view.findViewById<TextView>(R.id.tvStatTeachers)
        val tvAvgAttendance = view.findViewById<TextView>(R.id.tvAvgAttendanceRate)
        val chartAttendance = view.findViewById<DonutChartView>(R.id.chartAdminAttendance)
        val legendAttendance = view.findViewById<View>(R.id.legendAdminAttendance)
        val layoutActiveSessions = view.findViewById<LinearLayout>(R.id.layoutActiveSessions)

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            tvStudents.text = stats["students"].toString()
            tvClasses.text = stats["classes"].toString()
            tvTeachers.text = stats["teachers"].toString()
        }

        // The headline rate and the chart are computed from the same records, so they always agree
        statsViewModel.systemOverview.observe(viewLifecycleOwner) { overview ->
            tvAvgAttendance.text = overview.attendanceRate?.let { "$it%" } ?: "--"
            AttendanceViews.bindChart(chartAttendance, legendAttendance, overview)
        }

        statsViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                statsViewModel.clearErrorMessage()
            }
        }

        view.findViewById<View>(R.id.btnAdminAiAssistant).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.adminFragmentContainer, AiAssistantFragment.newInstance(showBackButton = true))
                .addToBackStack(null)
                .commit()
        }

        viewModel.activeSessions.observe(viewLifecycleOwner) { sessions ->
            layoutActiveSessions.removeAllViews()
            if (sessions.isEmpty()) {
                AttendanceViews.addEmptyNote(layoutActiveSessions, "Không có phiên điểm danh nào đang diễn ra.")
            } else {
                for ((className, date) in sessions) {
                    val card = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_active_session, layoutActiveSessions, false)
                    card.findViewById<TextView>(R.id.tvActiveSessionClass).text = className
                    card.findViewById<TextView>(R.id.tvActiveSessionDate).text = "Ngày $date • Đang mở"
                    layoutActiveSessions.addView(card)
                }
            }
        }

        viewModel.loadDashboardStats()
        viewModel.loadActiveSessions()
        statsViewModel.loadSystemOverview()

        return view
    }
}
