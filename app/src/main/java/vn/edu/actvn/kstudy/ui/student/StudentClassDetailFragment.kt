package vn.edu.actvn.kstudy.ui.student

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.adapter.AttendanceHistoryAdapter
import vn.edu.actvn.kstudy.ui.common.AttendanceViews
import vn.edu.actvn.kstudy.ui.common.FormFields
import vn.edu.actvn.kstudy.util.LeaveRequestRules
import vn.edu.actvn.kstudy.viewmodel.StudentViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        val tvHistoryEmpty = view.findViewById<View>(R.id.tvStudentClassHistoryEmpty)

        viewModel.history.observe(viewLifecycleOwner) { history ->
            historyAdapter.updateList(history)
            tvHistoryEmpty.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE

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

        val layoutLeaveRequests = view.findViewById<LinearLayout>(R.id.layoutStudentLeaveRequests)
        view.findViewById<View>(R.id.btnRequestLeave).setOnClickListener {
            showLeaveRequestDialog()
        }

        viewModel.leaveRequests.observe(viewLifecycleOwner) { requests ->
            layoutLeaveRequests.removeAllViews()
            if (requests.isEmpty()) {
                AttendanceViews.addEmptyNote(layoutLeaveRequests, "Bạn chưa gửi đơn xin nghỉ nào cho lớp này.")
            }
            for (request in requests) {
                AttendanceViews.addLeaveRequest(layoutLeaveRequests, request, forTeacher = false)
            }
        }

        viewModel.leaveResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                Toast.makeText(requireContext(), result.second, Toast.LENGTH_LONG).show()
                viewModel.clearLeaveResult()
            }
        }

        viewModel.loadHistory(studentId, classId)
        viewModel.loadLeaveRequests(studentId, classId)

        return view
    }

    // A request is for one day of this class; it may be sent before the lesson or afterwards
    private fun showLeaveRequestDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_leave_request, null)
        val etDate = content.findViewById<EditText>(R.id.etLeaveDate)
        val tilReason = content.findViewById<TextInputLayout>(R.id.tilLeaveReason)
        val etReason = content.findViewById<EditText>(R.id.etLeaveReason)

        val formatter = SimpleDateFormat(LeaveRequestRules.DATE_PATTERN, Locale.US)
        val day = Calendar.getInstance()
        etDate.setText(formatter.format(day.time))
        etDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    day.set(year, month, dayOfMonth)
                    etDate.setText(formatter.format(day.time))
                },
                day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xin nghỉ phép • $className")
            .setView(content)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Gửi đơn", null)
            .show()
        FormFields.clearErrorOnEdit(tilReason)
        // Set after show() so that an empty reason keeps the dialog open instead of closing it
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) {
                FormFields.setError(tilReason, "Vui lòng nhập lý do xin nghỉ")
            } else {
                viewModel.submitLeaveRequest(studentId, classId, etDate.text.toString(), reason)
                dialog.dismiss()
            }
        }
    }
}
