package vn.edu.actvn.kstudy.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.StudentAdapter
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel

class ClassRosterFragment : Fragment() {

    private var classId: String = ""
    private var className: String = ""
    private var academicYear: String = ""

    private lateinit var viewModel: AdminViewModel
    private lateinit var enrolledAdapter: StudentAdapter
    private lateinit var nonEnrolledAdapter: StudentAdapter

    companion object {
        fun newInstance(classId: String, className: String, academicYear: String): ClassRosterFragment {
            return ClassRosterFragment().apply {
                arguments = Bundle().apply {
                    putString("classId", classId)
                    putString("className", className)
                    putString("academicYear", academicYear)
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
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_class_roster, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        val tvSubject = view.findViewById<TextView>(R.id.tvRosterSubject)
        val tvClassName = view.findViewById<TextView>(R.id.tvRosterClassName)
        val tvEnrolledTitle = view.findViewById<TextView>(R.id.tvEnrolledTitle)
        val tvUnenrolledTitle = view.findViewById<TextView>(R.id.tvUnenrolledTitle)
        val btnBack = view.findViewById<View>(R.id.btnBackClassRoster)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tvSubject.text = className
        tvClassName.text = "Năm học: $academicYear"

        val rvEnrolled = view.findViewById<RecyclerView>(R.id.rvEnrolledStudents)
        val rvNonEnrolled = view.findViewById<RecyclerView>(R.id.rvNonEnrolledStudents)

        val cardToggle = view.findViewById<View>(R.id.cardToggleUnenrolled)
        val imgArrow = view.findViewById<android.widget.ImageView>(R.id.imgUnenrolledArrow)
        var isExpanded = false

        cardToggle.setOnClickListener {
            isExpanded = !isExpanded
            rvNonEnrolled.visibility = if (isExpanded) View.VISIBLE else View.GONE
            imgArrow.rotation = if (isExpanded) 180f else 0f
        }

        rvEnrolled.layoutManager = LinearLayoutManager(requireContext())
        rvNonEnrolled.layoutManager = LinearLayoutManager(requireContext())

        enrolledAdapter = StudentAdapter(
            list = emptyList(),
            onActionClick = { student ->
                viewModel.unenrollStudent(classId, student.id)
            },
            actionText = "Xóa khỏi lớp"
        )
        rvEnrolled.adapter = enrolledAdapter

        nonEnrolledAdapter = StudentAdapter(
            list = emptyList(),
            onActionClick = { student ->
                viewModel.enrollStudent(classId, student.id)
            },
            actionText = "Thêm vào lớp"
        )
        rvNonEnrolled.adapter = nonEnrolledAdapter

        viewModel.classStudents.observe(viewLifecycleOwner) { students ->
            enrolledAdapter.updateList(students)
            tvEnrolledTitle.text = "Danh sách lớp (${students.size} học sinh)"
        }

        viewModel.nonClassStudents.observe(viewLifecycleOwner) { students ->
            nonEnrolledAdapter.updateList(students)
            tvUnenrolledTitle.text = "Thêm học sinh vào lớp (${students.size} có thể thêm)"
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadEnrollmentStudents(classId)

        return view
    }
}
