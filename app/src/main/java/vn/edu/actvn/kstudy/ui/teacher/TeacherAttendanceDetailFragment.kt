package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.AttendanceRecordAdapter
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel
import java.text.SimpleDateFormat
import java.util.*

class TeacherAttendanceDetailFragment : Fragment() {

    private var sessionId: String = ""
    private var className: String = ""
    private var academicYear: String = ""

    private lateinit var viewModel: TeacherViewModel
    private lateinit var adapter: AttendanceRecordAdapter

    companion object {
        fun newInstance(sessionId: String, className: String, academicYear: String): TeacherAttendanceDetailFragment {
            return TeacherAttendanceDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("sessionId", sessionId)
                    putString("className", className)
                    putString("academicYear", academicYear)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            sessionId = it.getString("sessionId") ?: ""
            className = it.getString("className") ?: ""
            academicYear = it.getString("academicYear") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_attendance_detail, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvTitle = view.findViewById<TextView>(R.id.tvManualClassTitle)
        tvTitle.text = "$className ($academicYear)"

        val btnBack = view.findViewById<View>(R.id.btnBackAttendanceDetail)
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvManualAttendance)
        rv.layoutManager = LinearLayoutManager(requireContext())

        adapter = AttendanceRecordAdapter(emptyList()) { record, newStatus ->
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeMarked = if (newStatus == "ABSENT" || newStatus == "EXCUSED") "-" else formatter.format(Date())
            viewModel.updateRecordStatus(record.id, sessionId, newStatus, timeMarked)
        }
        rv.adapter = adapter

        val btnSave = view.findViewById<Button>(R.id.btnSaveManual)
        btnSave.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewModel.records.observe(viewLifecycleOwner) { records ->
            adapter.updateList(records)
        }

        viewModel.loadRecordsForSession(sessionId)

        return view
    }
}
