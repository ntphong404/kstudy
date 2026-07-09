package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.ui.adapter.AttendanceRecordAdapter
import vn.edu.actvn.kstudy.ui.common.QrView
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel

class TeacherQrCodeFragment : Fragment() {

    private var sessionId: String = ""
    private var classId: String = ""
    private var className: String = ""
    private var academicYear: String = ""
    private var qrText: String = ""

    private lateinit var viewModel: TeacherViewModel
    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (sessionId.isNotEmpty()) {
                viewModel.loadRecordsForSession(sessionId)
            }
            handler.postDelayed(this, 2000) // Poll database every 2 seconds
        }
    }

    companion object {
        fun newInstance(
            sessionId: String,
            classId: String,
            className: String,
            academicYear: String,
            qrText: String
        ): TeacherQrCodeFragment {
            return TeacherQrCodeFragment().apply {
                arguments = Bundle().apply {
                    putString("sessionId", sessionId)
                    putString("classId", classId)
                    putString("className", className)
                    putString("academicYear", academicYear)
                    putString("qrText", qrText)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            sessionId = it.getString("sessionId") ?: ""
            classId = it.getString("classId") ?: ""
            className = it.getString("className") ?: ""
            academicYear = it.getString("academicYear") ?: ""
            qrText = it.getString("qrText") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_qr_code, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvClassTitle = view.findViewById<TextView>(R.id.tvQrClassTitle)
        val qrView = view.findViewById<QrView>(R.id.qrView)
        val tvCode = view.findViewById<TextView>(R.id.tvManualCode)
        val tvStats = view.findViewById<TextView>(R.id.tvQrStats)
        val pbProgress = view.findViewById<ProgressBar>(R.id.pbQrProgress)
        val btnEnd = view.findViewById<Button>(R.id.btnEndSession)
        val btnBack = view.findViewById<View>(R.id.btnBackQrCode)
        
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        val btnZoomQr = view.findViewById<Button>(R.id.btnZoomQr)
        btnZoomQr.setOnClickListener {
            showFullScreenQrDialog(qrText)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvQrManualAttendance)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = AttendanceRecordAdapter(emptyList()) { record, newStatus ->
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val timeMarked = if (newStatus == "ABSENT") "-" else formatter.format(java.util.Date())
            viewModel.updateRecordStatus(record.id, sessionId, newStatus, timeMarked)
        }
        rv.adapter = adapter

        tvClassTitle.text = "$className ($academicYear)"
        qrView.setQrCodeText(qrText)
        tvCode.text = qrText

        viewModel.records.observe(viewLifecycleOwner) { records ->
            if (records != null) {
                val presentCount = records.filter { it.status == "PRESENT" || it.status == "LATE" }.size
                val total = records.size
                tvStats.text = "Đã điểm danh: $presentCount / $total học sinh"
                pbProgress.max = total
                pbProgress.progress = presentCount
                adapter.updateList(records)
            }
        }

        btnEnd.setOnClickListener {
            viewModel.closeSession(sessionId, classId)
            Toast.makeText(requireContext(), "Đã đóng phiên điểm danh!", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        }

        // Start polling
        handler.post(pollRunnable)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(pollRunnable) // Stop polling when fragment is destroyed
    }

    private fun showFullScreenQrDialog(qrText: String) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_screen_qr)
        
        val qrView = dialog.findViewById<vn.edu.actvn.kstudy.ui.common.QrView>(R.id.dialogQrView)
        val tvCode = dialog.findViewById<TextView>(R.id.dialogTvCode)
        val btnClose = dialog.findViewById<View>(R.id.btnDismissDialog)
        
        qrView.setQrCodeText(qrText)
        tvCode.text = qrText
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }
}
