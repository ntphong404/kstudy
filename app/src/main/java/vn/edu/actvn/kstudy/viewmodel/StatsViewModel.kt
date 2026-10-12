package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.ai.AiService
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource
import vn.edu.actvn.kstudy.util.AiPromptBuilder
import vn.edu.actvn.kstudy.util.AttendanceAnalyzer
import vn.edu.actvn.kstudy.util.AttendanceReportBuilder
import vn.edu.actvn.kstudy.util.ClassReport
import vn.edu.actvn.kstudy.util.StatusCount
import vn.edu.actvn.kstudy.util.StudentClassStat
import vn.edu.actvn.kstudy.util.XlsxWriter
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AiAnalysisState {
    object Idle : AiAnalysisState()
    object Loading : AiAnalysisState()
    data class Success(val text: String) : AiAnalysisState()
    data class Error(val message: String) : AiAnalysisState()
}

// An Excel file kept in memory until the screen saves and shares it
class ExportedReport(val fileName: String, val bytes: ByteArray)

class StatsViewModel(
    private val dataSource: AttendanceDataSource,
    private val aiService: AiService,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Date = { Date() }
) : ViewModel() {

    private val _classReport = MutableLiveData<ClassReport?>()
    val classReport: LiveData<ClassReport?> = _classReport

    private val _studentStats = MutableLiveData<List<StudentClassStat>>()
    val studentStats: LiveData<List<StudentClassStat>> = _studentStats

    private val _systemOverview = MutableLiveData<StatusCount>()
    val systemOverview: LiveData<StatusCount> = _systemOverview

    private val _aiAnalysis = MutableLiveData<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysis: LiveData<AiAnalysisState> = _aiAnalysis

    private val _exportedReport = MutableLiveData<ExportedReport?>()
    val exportedReport: LiveData<ExportedReport?> = _exportedReport

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadClassReport(classId: String) {
        viewModelScope.launch {
            try {
                val data = dataSource.getClassAttendance(classId)
                if (data == null) {
                    _classReport.value = null
                    _errorMessage.value = "Không tìm thấy lớp học!"
                } else {
                    _classReport.value = withContext(worker) { AttendanceAnalyzer.analyzeClass(data) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = LOAD_FAILED
            }
        }
    }

    fun loadStudentStats(studentId: String) {
        viewModelScope.launch {
            try {
                val classes = dataSource.getAttendanceForStudent(studentId)
                _studentStats.value = withContext(worker) { AttendanceAnalyzer.analyzeStudent(studentId, classes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = LOAD_FAILED
            }
        }
    }

    fun loadSystemOverview() {
        viewModelScope.launch {
            try {
                val classes = dataSource.getAllAttendance()
                _systemOverview.value = withContext(worker) {
                    classes.fold(StatusCount()) { acc, data -> acc + AttendanceAnalyzer.analyzeClass(data).overall }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = LOAD_FAILED
            }
        }
    }

    fun analyzeClassWithAi() {
        val report = _classReport.value ?: return
        if (_aiAnalysis.value == AiAnalysisState.Loading) return
        if (report.sessions.isEmpty()) {
            _aiAnalysis.value = AiAnalysisState.Error("Chưa có buổi điểm danh nào đã chốt để phân tích.")
            return
        }
        _aiAnalysis.value = AiAnalysisState.Loading
        viewModelScope.launch {
            _aiAnalysis.value = try {
                val answer = AiPromptBuilder.cleanReply(
                    aiService.generate(
                        AiPromptBuilder.classAnalysisInstruction(),
                        emptyList(),
                        AiPromptBuilder.classAnalysisPrompt(report)
                    )
                )
                if (answer.isEmpty()) AiAnalysisState.Error(AiChatViewModel.EMPTY_ANSWER) else AiAnalysisState.Success(answer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiServiceException) {
                AiAnalysisState.Error(e.message ?: AiChatViewModel.AI_FAILED)
            } catch (e: Exception) {
                AiAnalysisState.Error(AiChatViewModel.AI_FAILED)
            }
        }
    }

    fun exportClassReport() {
        val report = _classReport.value ?: return
        viewModelScope.launch {
            try {
                val date = now()
                val exportedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(date)
                val bytes = withContext(worker) {
                    val out = ByteArrayOutputStream()
                    XlsxWriter.write(AttendanceReportBuilder.build(report, exportedAt), out)
                    out.toByteArray()
                }
                _exportedReport.value = ExportedReport(
                    AttendanceReportBuilder.fileName(report.courseClass.name, timestamp),
                    bytes
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Xuất báo cáo thất bại!"
            }
        }
    }

    // The screen calls these after handling a one-shot event, so it is not replayed on rotation
    fun clearExportedReport() {
        _exportedReport.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private companion object {
        const val LOAD_FAILED = "Không tải được dữ liệu thống kê. Vui lòng kiểm tra kết nối mạng!"
    }
}
