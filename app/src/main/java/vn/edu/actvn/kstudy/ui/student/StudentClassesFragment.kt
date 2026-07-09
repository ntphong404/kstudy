package vn.edu.actvn.kstudy.ui.student

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.ClassSelectionAdapter
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel

class StudentClassesFragment : Fragment() {

    private var studentUserId: String = ""
    private lateinit var viewModel: StudentViewModel
    private lateinit var adapter: ClassSelectionAdapter

    companion object {
        fun newInstance(userId: String): StudentClassesFragment {
            return StudentClassesFragment().apply {
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
        val view = inflater.inflate(R.layout.fragment_student_classes, container, false)

        viewModel = ViewModelProvider(this)[StudentViewModel::class.java]

        val tvWelcome = view.findViewById<TextView>(R.id.tvStudentWelcome)
        val tvDetails = view.findViewById<TextView>(R.id.tvStudentSubDetails)
        val rv = view.findViewById<RecyclerView>(R.id.rvStudentClasses)

        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = ClassSelectionAdapter(emptyList()) { courseClass ->
            val studentId = viewModel.student.value?.id ?: ""
            val fragment = StudentClassDetailFragment.newInstance(studentId, courseClass.id, courseClass.name, courseClass.academicYear)
            parentFragmentManager.beginTransaction()
                .replace(R.id.studentFragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter

        viewModel.student.observe(viewLifecycleOwner) { student ->
            if (student != null) {
                tvWelcome.text = "Chào học sinh, ${student.name}"
                tvDetails.text = "Mã số: ${student.studentCode} • Lớp chuyên ngành: ${student.className}"
            }
        }

        viewModel.classes.observe(viewLifecycleOwner) { classes ->
            val rates = viewModel.attendanceRates.value ?: emptyMap()
            adapter.updateData(classes, rates)
        }

        viewModel.attendanceRates.observe(viewLifecycleOwner) { rates ->
            val classes = viewModel.classes.value ?: emptyList()
            adapter.updateData(classes, rates)
        }

        viewModel.loadStudent(studentUserId)

        return view
    }
}
