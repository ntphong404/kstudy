package vn.edu.actvn.kstudy.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.location.DeviceLocation
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.data.repository.LeaveRequestRepository
import vn.edu.actvn.kstudy.data.repository.StudentRepository
import vn.edu.actvn.kstudy.util.LeaveRequestRules

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application.applicationContext)
    private val leaveRepository = LeaveRequestRepository()

    private val _student = MutableLiveData<Student?>()
    val student: LiveData<Student?> = _student

    private val _classes = MutableLiveData<List<CourseClass>>()
    val classes: LiveData<List<CourseClass>> = _classes

    private val _attendanceRates = MutableLiveData<Map<String, Int>>()
    val attendanceRates: LiveData<Map<String, Int>> = _attendanceRates

    private val _history = MutableLiveData<List<AttendanceRecord>>()
    val history: LiveData<List<AttendanceRecord>> = _history

    private val _attendanceResult = MutableLiveData<Pair<Boolean, String>?>()
    val attendanceResult: LiveData<Pair<Boolean, String>?> = _attendanceResult

    // True while a scan is being checked (code, device, position)
    private val _checkingIn = MutableLiveData(false)
    val checkingIn: LiveData<Boolean> = _checkingIn

    private val _leaveRequests = MutableLiveData<List<LeaveRequest>>()
    val leaveRequests: LiveData<List<LeaveRequest>> = _leaveRequests

    private val _leaveResult = MutableLiveData<Pair<Boolean, String>?>()
    val leaveResult: LiveData<Pair<Boolean, String>?> = _leaveResult

    fun loadStudent(userId: String) {
        viewModelScope.launch {
            val s = repository.getStudentByUserId(userId)
            _student.value = s
            if (s != null) {
                loadClasses(s.id)
            }
        }
    }

    fun loadClasses(studentId: String) {
        viewModelScope.launch {
            val list = repository.getClassesForStudent(studentId)
            _classes.value = list

            val rates = mutableMapOf<String, Int>()
            for (c in list) {
                rates[c.id] = repository.getAttendanceRate(studentId, c.id)
            }
            _attendanceRates.value = rates
        }
    }

    fun loadHistory(studentId: String, classId: String) {
        viewModelScope.launch {
            _history.value = repository.getAttendanceHistoryForStudentInClass(studentId, classId)
        }
    }

    // scannedAt: the moment the code was scanned or typed, see StudentRepository.markAttendanceViaCode
    fun markAttendance(studentId: String, code: String, scannedAt: Long) {
        if (code.isBlank()) {
            _attendanceResult.value = Pair(false, "Vui lòng nhập mã hoặc quét mã QR!")
            return
        }
        if (_checkingIn.value == true) return
        _checkingIn.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val res = repository.markAttendanceViaCode(studentId, code, scannedAt, deviceId(app)) {
                DeviceLocation.current(app)
            }
            _checkingIn.value = false
            _attendanceResult.value = res
            if (res.first) {
                loadClasses(studentId)
            }
        }
    }

    fun clearAttendanceResult() {
        _attendanceResult.value = null
    }

    fun loadLeaveRequests(studentId: String, classId: String) {
        viewModelScope.launch {
            try {
                _leaveRequests.value = LeaveRequestRules.sorted(leaveRepository.getForStudent(studentId, classId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _leaveResult.value = Pair(false, "Không tải được đơn xin nghỉ!")
            }
        }
    }

    fun submitLeaveRequest(studentId: String, classId: String, date: String, reason: String) {
        viewModelScope.launch {
            _leaveResult.value = try {
                val existing = leaveRepository.getForStudent(studentId, classId)
                val problem = LeaveRequestRules.problemWith(date, reason, existing)
                val student = if (problem == null) repository.getStudentByUserId(studentId) else null
                when {
                    problem != null -> Pair(false, problem)
                    student == null -> Pair(false, "Lỗi tải thông tin học sinh!")
                    else -> {
                        leaveRepository.submit(
                            LeaveRequest(
                                classId = classId,
                                studentId = studentId,
                                studentName = student.name,
                                studentCode = student.studentCode,
                                date = date,
                                reason = reason.trim(),
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        Pair(true, "Đã gửi đơn xin nghỉ, chờ giáo viên duyệt.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Pair(false, "Không gửi được đơn xin nghỉ. Vui lòng thử lại!")
            }
            loadLeaveRequests(studentId, classId)
        }
    }

    fun clearLeaveResult() {
        _leaveResult.value = null
    }

    // Stays the same for this app on this phone until a factory reset; identifies the phone,
    // not the account, so it tells when two accounts check in from the same phone.
    @SuppressLint("HardwareIds")
    private fun deviceId(app: Application): String =
        Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
}
