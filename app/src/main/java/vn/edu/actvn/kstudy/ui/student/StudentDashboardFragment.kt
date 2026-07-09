package vn.edu.actvn.kstudy.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel

class StudentDashboardFragment : Fragment() {

    private var studentUserId: String = ""
    private lateinit var viewModel: StudentViewModel

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

        val tvWelcome = view.findViewById<TextView>(R.id.tvStudentDashboardWelcome)
        val tvDetails = view.findViewById<TextView>(R.id.tvStudentDashboardSubDetails)
        val tvStatClasses = view.findViewById<TextView>(R.id.tvStudentStatClasses)
        val tvStatAttendance = view.findViewById<TextView>(R.id.tvStudentStatAttendance)
        val tvUpcomingName = view.findViewById<TextView>(R.id.tvStudentUpcomingClassName)
        val tvUpcomingInfo = view.findViewById<TextView>(R.id.tvStudentUpcomingClassInfo)

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

        viewModel.attendanceRates.observe(viewLifecycleOwner) { rates ->
            tvStatAttendance.text = if (rates.isNotEmpty()) {
                "${rates.values.average().toInt()}%"
            } else {
                "--"
            }
        }

        viewModel.loadStudent(studentUserId)

        return view
    }
}
