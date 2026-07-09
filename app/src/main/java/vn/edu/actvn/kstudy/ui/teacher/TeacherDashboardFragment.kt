package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel

class TeacherDashboardFragment : Fragment() {

    private var teacherUserId: String = ""
    private lateinit var viewModel: TeacherViewModel

    companion object {
        fun newInstance(userId: String): TeacherDashboardFragment {
            return TeacherDashboardFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            teacherUserId = it.getString("userId") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvWelcome = view.findViewById<TextView>(R.id.tvTeacherDashboardWelcome)
        val tvDetails = view.findViewById<TextView>(R.id.tvTeacherDashboardSubDetails)
        val tvStatClasses = view.findViewById<TextView>(R.id.tvTeacherStatClasses)
        val tvStatStudents = view.findViewById<TextView>(R.id.tvTeacherStatStudents)
        val tvUpcomingName = view.findViewById<TextView>(R.id.tvTeacherUpcomingClassName)
        val tvUpcomingInfo = view.findViewById<TextView>(R.id.tvTeacherUpcomingClassInfo)

        viewModel.teacher.observe(viewLifecycleOwner) { teacher ->
            if (teacher != null) {
                tvWelcome.text = "Xin chào, ${teacher.name}"
                tvDetails.text = "Môn học phụ trách: ${teacher.subject} • SĐT: ${teacher.phone}"
            }
        }

        viewModel.classes.observe(viewLifecycleOwner) { classes ->
            tvStatClasses.text = classes.size.toString()
            tvStatStudents.text = classes.sumOf { it.totalStudents }.toString()

            val upcoming = ScheduleUtils.findUpcomingClass(classes)
            if (upcoming != null) {
                tvUpcomingName.text = upcoming.courseClass.name
                tvUpcomingInfo.text =
                    "${upcoming.dayLabel} • ${upcoming.timeLabel} • Phòng ${upcoming.courseClass.room}"
            } else {
                tvUpcomingName.text = "Chưa có lịch học"
                tvUpcomingInfo.text = "Bạn chưa được phân công lớp học nào có lịch cụ thể."
            }
        }

        viewModel.loadTeacher(teacherUserId)

        return view
    }
}
