package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.StudentAdapter
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel

class TeacherClassDetailFragment : Fragment() {

    private var classId: String = ""
    private var className: String = ""
    private var academicYear: String = ""
    private var schedule: String = ""

    private lateinit var viewModel: TeacherViewModel
    private lateinit var studentAdapter: StudentAdapter

    companion object {
        fun newInstance(
            classId: String, className: String, academicYear: String, schedule: String
        ): TeacherClassDetailFragment {
            return TeacherClassDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("classId", classId)
                    putString("className", className)
                    putString("academicYear", academicYear)
                    putString("schedule", schedule)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            classId = it.getString("classId") ?: ""
            className = it.getString("className") ?: ""
            academicYear = it.getString("academicYear") ?: ""
            schedule = it.getString("schedule") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_class_detail, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvSubject = view.findViewById<TextView>(R.id.tvDetailSubject)
        val tvInfo = view.findViewById<TextView>(R.id.tvDetailClassInfo)
        
        tvSubject.text = className
        tvInfo.text = "Năm học: $academicYear • Sĩ số: -- học sinh"

        val btnBack = view.findViewById<View>(R.id.btnBackClassDetail)
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val layoutNoActive = view.findViewById<View>(R.id.layoutNoActiveSession)
        val layoutActive = view.findViewById<View>(R.id.layoutActiveSession)
        val btnCreate = view.findViewById<Button>(R.id.btnCreateSession)
        val btnOpenActive = view.findViewById<Button>(R.id.btnOpenActiveSession)
        val tvScheduleHint = view.findViewById<TextView>(R.id.tvScheduleHint)
        val layoutSessionHistoryHeader = view.findViewById<LinearLayout>(R.id.layoutSessionHistoryHeader)
        val ivSessionHistoryToggle = view.findViewById<ImageView>(R.id.ivSessionHistoryToggle)
        val layoutSessionHistory = view.findViewById<LinearLayout>(R.id.layoutSessionHistory)

        layoutSessionHistoryHeader.setOnClickListener {
            val expand = layoutSessionHistory.visibility != View.VISIBLE
            layoutSessionHistory.visibility = if (expand) View.VISIBLE else View.GONE
            ivSessionHistoryToggle.rotation = if (expand) 0f else -90f
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvClassDetailStudents)
        rv.layoutManager = LinearLayoutManager(requireContext())
        studentAdapter = StudentAdapter(emptyList())
        rv.adapter = studentAdapter

        val canOpenNow = ScheduleUtils.isWithinScheduledTime(schedule)
        btnCreate.isEnabled = canOpenNow
        tvScheduleHint.text = if (schedule.isBlank()) {
            "Lớp học chưa có lịch học cụ thể."
        } else if (canOpenNow) {
            "Lịch học: $schedule • Đang trong giờ học, có thể mở điểm danh."
        } else {
            "Lịch học: $schedule • Chỉ có thể mở điểm danh trong khung giờ học."
        }

        btnCreate.setOnClickListener {
            val code = generateRandomCode()
            viewModel.createSession(classId, code)
        }

        btnOpenActive.setOnClickListener {
            val sess = viewModel.activeSession.value
            if (sess != null) {
                navigateToQrCodeScreen(sess.id, sess.qrCode)
            }
        }

        view.findViewById<View>(R.id.btnOpenClassStats).setOnClickListener {
            val fragment = TeacherClassStatsFragment.newInstance(classId, className)
            parentFragmentManager.beginTransaction()
                .replace(R.id.teacherFragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        viewModel.activeSession.observe(viewLifecycleOwner) { sess ->
            if (sess != null) {
                layoutNoActive.visibility = View.GONE
                layoutActive.visibility = View.VISIBLE
            } else {
                layoutNoActive.visibility = View.VISIBLE
                layoutActive.visibility = View.GONE
            }
        }

        viewModel.classStudents.observe(viewLifecycleOwner) { students ->
            studentAdapter.updateList(students)
            tvInfo.text = "Năm học: $academicYear • Sĩ số: ${students.size} học sinh"
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { (success, message) ->
            if (success && message.contains("Mở phiên")) {
                val sess = viewModel.activeSession.value
                if (sess != null) {
                    navigateToQrCodeScreen(sess.id, sess.qrCode)
                }
            } else if (!success) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.pastSessions.observe(viewLifecycleOwner) { sessions ->
            layoutSessionHistory.removeAllViews()
            if (sessions.isEmpty()) {
                val empty = TextView(requireContext()).apply {
                    text = "Chưa có phiên điểm danh nào trước đó."
                    setTextColor(resources.getColor(R.color.text_secondary, null))
                    textSize = 13f
                }
                layoutSessionHistory.addView(empty)
            } else {
                for (session in sessions) {
                    val item = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_session_history, layoutSessionHistory, false)
                    item.findViewById<TextView>(R.id.tvSessionHistoryDate).text = "Ngày ${session.date}"
                    item.findViewById<TextView>(R.id.tvSessionHistorySummary).text = "Mã: ${session.qrCode} • Chạm để xem/chỉnh sửa"
                    item.setOnClickListener {
                        val fragment = TeacherAttendanceDetailFragment.newInstance(session.id, className, academicYear)
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.teacherFragmentContainer, fragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    layoutSessionHistory.addView(item)
                }
            }
        }

        viewModel.loadClassStudents(classId)
        viewModel.loadActiveSession(classId)
        viewModel.loadPastSessions(classId)

        return view
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Omit I, O, 0, 1 for clarity
        return (1..4).map { chars.random() }.joinToString("")
    }

    private fun navigateToQrCodeScreen(sessionId: String, qrText: String) {
        val fragment = TeacherQrCodeFragment.newInstance(sessionId, classId, className, academicYear, qrText)
        parentFragmentManager.beginTransaction()
            .replace(R.id.teacherFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }
}
