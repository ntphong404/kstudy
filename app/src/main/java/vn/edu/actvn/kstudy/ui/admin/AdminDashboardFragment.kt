package vn.edu.actvn.kstudy.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel

class AdminDashboardFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_admin_dashboard, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        val tvStudents = view.findViewById<TextView>(R.id.tvStatStudents)
        val tvClasses = view.findViewById<TextView>(R.id.tvStatClasses)
        val tvTeachers = view.findViewById<TextView>(R.id.tvStatTeachers)
        val tvAvgAttendance = view.findViewById<TextView>(R.id.tvAvgAttendanceRate)
        val layoutActiveSessions = view.findViewById<LinearLayout>(R.id.layoutActiveSessions)

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            tvStudents.text = stats["students"].toString()
            tvClasses.text = stats["classes"].toString()
            tvTeachers.text = stats["teachers"].toString()
            tvAvgAttendance.text = "${stats["avg_attendance"]}%"
        }

        viewModel.activeSessions.observe(viewLifecycleOwner) { sessions ->
            layoutActiveSessions.removeAllViews()
            if (sessions.isEmpty()) {
                val empty = TextView(requireContext()).apply {
                    text = "Không có phiên điểm danh nào đang diễn ra."
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    textSize = 13f
                }
                layoutActiveSessions.addView(empty)
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

        return view
    }
}
