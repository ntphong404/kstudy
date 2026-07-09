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
import vn.edu.actvn.kstudy.ui.adapter.AttendanceHistoryAdapter
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel

class StudentClassDetailFragment : Fragment() {

    private var studentId: String = ""
    private var classId: String = ""
    private var className: String = ""
    private var academicYear: String = ""

    private lateinit var viewModel: StudentViewModel
    private lateinit var historyAdapter: AttendanceHistoryAdapter

    companion object {
        fun newInstance(
            studentId: String,
            classId: String,
            className: String,
            academicYear: String
        ): StudentClassDetailFragment {
            return StudentClassDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("studentId", studentId)
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
            studentId = it.getString("studentId") ?: ""
            classId = it.getString("classId") ?: ""
            className = it.getString("className") ?: ""
            academicYear = it.getString("academicYear") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_student_class_detail, container, false)

        viewModel = ViewModelProvider(this)[StudentViewModel::class.java]

        val tvSubject = view.findViewById<TextView>(R.id.tvStudentDetailSubject)
        val tvInfo = view.findViewById<TextView>(R.id.tvStudentDetailClassInfo)
        val btnBack = view.findViewById<View>(R.id.btnBackStudentDetail)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        tvSubject.text = className
        tvInfo.text = "Năm học: $academicYear"

        val tvRate = view.findViewById<TextView>(R.id.tvStudentDetailRate)
        val tvPresent = view.findViewById<TextView>(R.id.tvStudentDetailPresentCount)
        val tvAbsent = view.findViewById<TextView>(R.id.tvStudentDetailAbsentCount)

        val rv = view.findViewById<RecyclerView>(R.id.rvStudentClassHistory)
        rv.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = AttendanceHistoryAdapter(emptyList())
        rv.adapter = historyAdapter

        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.updateList(history)

            val total = history.size
            if (total > 0) {
                val present = history.filter { it.status == "PRESENT" }.size
                val late = history.filter { it.status == "LATE" }.size
                val absent = history.filter { it.status == "ABSENT" }.size
                val rate = ((present + late) * 100) / total

                tvRate.text = "$rate%"
                tvPresent.text = "$present"
                tvAbsent.text = "$absent"
            } else {
                tvRate.text = "100%"
                tvPresent.text = "0"
                tvAbsent.text = "0"
            }
        }

        viewModel.loadHistory(studentId, classId)

        return view
    }
}
