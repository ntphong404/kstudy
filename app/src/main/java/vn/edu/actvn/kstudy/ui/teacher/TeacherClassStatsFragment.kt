package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.common.AttendanceViews
import vn.edu.actvn.kstudy.ui.common.BarChartView
import vn.edu.actvn.kstudy.ui.common.DonutChartView
import vn.edu.actvn.kstudy.ui.common.ReportSharer
import vn.edu.actvn.kstudy.util.AttendanceAnalyzer
import vn.edu.actvn.kstudy.util.ClassReport
import vn.edu.actvn.kstudy.util.SessionDates
import vn.edu.actvn.kstudy.viewmodel.AiAnalysisState
import vn.edu.actvn.kstudy.viewmodel.ExportedReport
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import vn.edu.actvn.kstudy.viewmodel.StatsViewModel

// Charts, at-risk students, AI analysis and Excel export for one class
class TeacherClassStatsFragment : Fragment() {

    private var classId: String = ""
    private var className: String = ""

    private lateinit var viewModel: StatsViewModel

    companion object {
        // The bar chart stays readable with at most this many sessions
        private const val MAX_BARS = 10

        fun newInstance(classId: String, className: String): TeacherClassStatsFragment {
            return TeacherClassStatsFragment().apply {
                arguments = Bundle().apply {
                    putString("classId", classId)
                    putString("className", className)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            classId = it.getString("classId") ?: ""
            className = it.getString("className") ?: ""
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_teacher_class_stats, container, false)

        viewModel = ViewModelProvider(this, KstudyViewModelFactory)[StatsViewModel::class.java]

        val btnBack = view.findViewById<View>(R.id.btnBackClassStats)
        val tvClassName = view.findViewById<TextView>(R.id.tvStatsClassName)
        val chartOverall = view.findViewById<DonutChartView>(R.id.chartClassOverall)
        val legendOverall = view.findViewById<View>(R.id.legendClassOverall)
        val tvSummary = view.findViewById<TextView>(R.id.tvStatsSummary)
        val tvBarTitle = view.findViewById<TextView>(R.id.tvStatsBarTitle)
        val chartSessions = view.findViewById<BarChartView>(R.id.chartClassSessions)
        val tvRiskTitle = view.findViewById<TextView>(R.id.tvStatsRiskTitle)
        val layoutRisk = view.findViewById<LinearLayout>(R.id.layoutStatsRisk)
        val tvAiAnalysis = view.findViewById<TextView>(R.id.tvAiAnalysis)
        val pbAiAnalysis = view.findViewById<ProgressBar>(R.id.pbAiAnalysis)
        val btnAiAnalysis = view.findViewById<Button>(R.id.btnAiAnalysis)
        val btnExport = view.findViewById<Button>(R.id.btnExportExcel)

        tvClassName.text = className
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewModel.classReport.observe(viewLifecycleOwner) { report ->
            if (report == null) return@observe

            AttendanceViews.bindChart(chartOverall, legendOverall, report.overall)
            tvSummary.text = "Sĩ số: ${report.students.size} học sinh • Đã chốt ${report.sessions.size} buổi điểm danh"

            val shown = report.sessions.takeLast(MAX_BARS)
            tvBarTitle.text = if (report.sessions.size > MAX_BARS) {
                "Tỷ lệ đi học theo buổi ($MAX_BARS buổi gần nhất)"
            } else {
                "Tỷ lệ đi học theo buổi"
            }
            chartSessions.setBars(
                shown.map { stat ->
                    BarChartView.Bar(SessionDates.dayMonth(stat.session.date), stat.count.attendanceRate ?: 0)
                }
            )

            showAtRiskStudents(report, tvRiskTitle, layoutRisk)

            btnAiAnalysis.isEnabled = true
            btnExport.isEnabled = true
        }

        viewModel.aiAnalysis.observe(viewLifecycleOwner) { state ->
            pbAiAnalysis.visibility = if (state is AiAnalysisState.Loading) View.VISIBLE else View.GONE
            btnAiAnalysis.isEnabled = state !is AiAnalysisState.Loading && viewModel.classReport.value != null
            when (state) {
                is AiAnalysisState.Idle -> Unit
                is AiAnalysisState.Loading -> {
                    tvAiAnalysis.text = "AI đang đọc số liệu điểm danh của lớp…"
                    tvAiAnalysis.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
                is AiAnalysisState.Success -> {
                    tvAiAnalysis.text = state.text
                    tvAiAnalysis.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
                    btnAiAnalysis.text = "Phân tích lại"
                }
                is AiAnalysisState.Error -> {
                    tvAiAnalysis.text = state.message
                    tvAiAnalysis.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
                }
            }
        }

        viewModel.exportedReport.observe(viewLifecycleOwner) { report ->
            if (report != null) {
                viewModel.clearExportedReport()
                showExportedDialog(report)
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
                tvSummary.text = "Chưa tải được dữ liệu."
            }
        }

        btnAiAnalysis.setOnClickListener {
            viewModel.analyzeClassWithAi()
        }

        btnExport.setOnClickListener {
            viewModel.exportClassReport()
        }

        viewModel.loadClassReport(classId)

        return view
    }

    private fun showAtRiskStudents(report: ClassReport, tvTitle: TextView, container: LinearLayout) {
        val atRisk = report.atRisk
        tvTitle.text = "Học sinh cần lưu ý (${atRisk.size})"
        container.removeAllViews()
        if (atRisk.isEmpty()) {
            AttendanceViews.addEmptyNote(
                container,
                if (report.sessions.isEmpty()) {
                    "Chưa có buổi điểm danh nào đã chốt."
                } else {
                    "Không có học sinh nào vắng quá ${AttendanceAnalyzer.WARNING_THRESHOLD_PERCENT}% số buổi."
                }
            )
            return
        }
        for (stat in atRisk) {
            AttendanceViews.addWarning(
                container = container,
                risk = stat.risk,
                title = "${stat.student.name} (${stat.student.studentCode})",
                body = "${AttendanceAnalyzer.riskLabel(stat.risk)} • ${AttendanceAnalyzer.absenceSummary(stat.count)}"
            )
        }
    }

    private fun showExportedDialog(report: ExportedReport) {
        val context = requireContext()
        val uri = try {
            ReportSharer.save(context, report)
        } catch (e: Exception) {
            Toast.makeText(context, "Không lưu được file báo cáo!", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Đã xuất báo cáo")
            .setMessage(report.fileName)
            .setPositiveButton("Chia sẻ") { _, _ ->
                ReportSharer.share(context, uri, "Báo cáo điểm danh $className")
            }
            .setNeutralButton("Mở file") { _, _ ->
                if (!ReportSharer.open(context, uri)) {
                    Toast.makeText(
                        context,
                        "Máy chưa có ứng dụng mở file Excel. Hãy dùng Chia sẻ để gửi file đi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
