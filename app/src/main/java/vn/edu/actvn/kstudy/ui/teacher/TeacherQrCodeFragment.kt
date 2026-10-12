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
import vn.edu.actvn.kstudy.util.CheckInRules
import vn.edu.actvn.kstudy.util.RotatingCode
import vn.edu.actvn.kstudy.viewmodel.TeacherViewModel

class TeacherQrCodeFragment : Fragment() {

    private var sessionId: String = ""
    private var classId: String = ""
    private var className: String = ""
    private var academicYear: String = ""
    private var secret: String = ""
    private var checksLocation: Boolean = false
    private var lateAfterMinutes: Int = 0

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

    // Everywhere the rotating code is currently displayed: the card, plus the full screen dialog while it is open
    private class CodeViews(val qr: QrView, val code: TextView, val countdown: TextView)

    private var cardViews: CodeViews? = null
    private var dialogViews: CodeViews? = null

    private val codeRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            showCode(now)
            handler.postDelayed(this, 1000 - now % 1000) // again at the start of the next second
        }
    }

    companion object {
        fun newInstance(
            sessionId: String,
            classId: String,
            className: String,
            academicYear: String,
            secret: String,
            checksLocation: Boolean,
            lateAfterMinutes: Int
        ): TeacherQrCodeFragment {
            return TeacherQrCodeFragment().apply {
                arguments = Bundle().apply {
                    putString("sessionId", sessionId)
                    putString("classId", classId)
                    putString("className", className)
                    putString("academicYear", academicYear)
                    putString("secret", secret)
                    putBoolean("checksLocation", checksLocation)
                    putInt("lateAfterMinutes", lateAfterMinutes)
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
            secret = it.getString("secret") ?: ""
            checksLocation = it.getBoolean("checksLocation")
            lateAfterMinutes = it.getInt("lateAfterMinutes")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_qr_code, container, false)

        viewModel = ViewModelProvider(this)[TeacherViewModel::class.java]

        val tvClassTitle = view.findViewById<TextView>(R.id.tvQrClassTitle)
        val tvStats = view.findViewById<TextView>(R.id.tvQrStats)
        val tvRules = view.findViewById<TextView>(R.id.tvQrRules)
        val pbProgress = view.findViewById<ProgressBar>(R.id.pbQrProgress)
        val btnEnd = view.findViewById<Button>(R.id.btnEndSession)
        val btnBack = view.findViewById<View>(R.id.btnBackQrCode)

        cardViews = CodeViews(
            qr = view.findViewById(R.id.qrView),
            code = view.findViewById(R.id.tvManualCode),
            countdown = view.findViewById(R.id.tvQrCountdown)
        )

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val btnZoomQr = view.findViewById<Button>(R.id.btnZoomQr)
        btnZoomQr.setOnClickListener {
            showFullScreenQrDialog()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvQrManualAttendance)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = AttendanceRecordAdapter(emptyList()) { record, newStatus ->
            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val timeMarked = if (newStatus == "ABSENT" || newStatus == "EXCUSED") "-" else formatter.format(java.util.Date())
            viewModel.updateRecordStatus(record.id, sessionId, newStatus, timeMarked)
        }
        rv.adapter = adapter

        tvClassTitle.text = "$className ($academicYear)"

        val locationRule = if (checksLocation) {
            "Vị trí: trong bán kính ${CheckInRules.MAX_DISTANCE_METERS} m"
        } else {
            "Vị trí: không kiểm tra (chưa lấy được vị trí của giáo viên)"
        }
        val lateRule = if (lateAfterMinutes > 0) " • Đi muộn: quét sau $lateAfterMinutes phút" else ""
        tvRules.text = locationRule + lateRule

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
        handler.post(codeRunnable)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(pollRunnable) // Stop polling when fragment is destroyed
        handler.removeCallbacks(codeRunnable)
        cardViews = null
    }

    // Draws the code of this moment; the QR image is only redrawn when the code has actually changed
    private fun showCode(now: Long) {
        val code = RotatingCode.codeAt(secret, now)
        val countdown = "Tự đổi sau ${RotatingCode.secondsLeft(now)} giây"
        for (views in listOfNotNull(cardViews, dialogViews)) {
            if (views.code.text.toString() != code) {
                views.qr.setQrCodeText(code)
                views.code.text = code
            }
            views.countdown.text = countdown
        }
    }

    private fun showFullScreenQrDialog() {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_screen_qr)

        dialogViews = CodeViews(
            qr = dialog.findViewById(R.id.dialogQrView),
            code = dialog.findViewById(R.id.dialogTvCode),
            countdown = dialog.findViewById(R.id.dialogTvCountdown)
        )
        showCode(System.currentTimeMillis())

        val btnClose = dialog.findViewById<View>(R.id.btnDismissDialog)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnDismissListener { dialogViews = null }
        dialog.show()
    }
}
