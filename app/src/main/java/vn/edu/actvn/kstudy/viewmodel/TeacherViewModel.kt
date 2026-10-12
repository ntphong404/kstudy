package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.location.DeviceLocation
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.data.repository.LeaveRequestRepository
import vn.edu.actvn.kstudy.data.repository.TeacherRepository
import vn.edu.actvn.kstudy.util.LeaveRequestRules
import vn.edu.actvn.kstudy.util.SessionDates

class TeacherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeacherRepository(application.applicationContext)
    private val leaveRepository = LeaveRequestRepository()

    private val _teacher = MutableLiveData<Teacher?>()
    val teacher: LiveData<Teacher?> = _teacher

    private val _classes = MutableLiveData<List<CourseClass>>()
    val classes: LiveData<List<CourseClass>> = _classes

    private val _classStudents = MutableLiveData<List<Student>>()
    val classStudents: LiveData<List<Student>> = _classStudents

    private val _activeSession = MutableLiveData<AttendanceSession?>()
    val activeSession: LiveData<AttendanceSession?> = _activeSession

    // True while the phone is being located and the session is being created
    private val _openingSession = MutableLiveData(false)
    val openingSession: LiveData<Boolean> = _openingSession

    // The session that has just been opened. The screen clears it after moving to the QR code,
    // so coming back does not open the QR code a second time.
    private val _openedSession = MutableLiveData<AttendanceSession?>()
    val openedSession: LiveData<AttendanceSession?> = _openedSession

    private val _records = MutableLiveData<List<AttendanceRecord>>()
    val records: LiveData<List<AttendanceRecord>> = _records

    private val _pastSessions = MutableLiveData<List<AttendanceSession>>()
    val pastSessions: LiveData<List<AttendanceSession>> = _pastSessions

    private val _leaveRequests = MutableLiveData<List<LeaveRequest>>()
    val leaveRequests: LiveData<List<LeaveRequest>> = _leaveRequests

    private val _operationResult = MutableLiveData<Pair<Boolean, String>>()
    val operationResult: LiveData<Pair<Boolean, String>> = _operationResult

    fun loadTeacher(userId: String) {
        viewModelScope.launch {
            val t = repository.getTeacherByUserId(userId)
            _teacher.value = t
            if (t != null) {
                loadClasses(t.id)
            }
        }
    }

    fun loadClasses(teacherId: String) {
        viewModelScope.launch {
            _classes.value = repository.getClassesForTeacher(teacherId)
        }
    }

    fun loadClassStudents(classId: String) {
        viewModelScope.launch {
            _classStudents.value = repository.getStudentsForClass(classId)
        }
    }

    fun loadActiveSession(classId: String) {
        viewModelScope.launch {
            val sess = repository.getActiveSession(classId)
            _activeSession.value = sess
            if (sess != null) {
                loadRecordsForSession(sess.id)
            } else {
                _records.value = emptyList()
            }
        }
    }

    fun createSession(classId: String, lateAfterMinutes: Int) {
        if (_openingSession.value == true) return
        _openingSession.value = true
        viewModelScope.launch {
            val session = try {
                // Null when the teacher refused the permission or the phone has no position:
                // the session is then opened without a location check.
                val position = DeviceLocation.current(getApplication())
                val sessId = repository.createAttendanceSession(classId, lateAfterMinutes, position)
                if (sessId.isNotEmpty()) repository.getActiveSession(classId) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _openingSession.value = false
            if (session != null) {
                _activeSession.value = session
                _openedSession.value = session
            } else {
                _operationResult.value = Pair(false, "Mở phiên điểm danh thất bại!")
            }
        }
    }

    fun clearOpenedSession() {
        _openedSession.value = null
    }

    fun closeSession(sessionId: String, classId: String) {
        viewModelScope.launch {
            repository.closeAttendanceSession(sessionId)
            loadActiveSession(classId)
            _operationResult.value = Pair(true, "Đã đóng phiên điểm danh!")
        }
    }

    fun loadRecordsForSession(sessionId: String) {
        viewModelScope.launch {
            _records.value = repository.getRecordsForSession(sessionId)
        }
    }

    fun updateRecordStatus(recordId: String, sessionId: String, status: String, timeMarked: String) {
        viewModelScope.launch {
            val ok = repository.updateRecordStatus(recordId, status, timeMarked)
            if (ok) {
                loadRecordsForSession(sessionId)
            }
        }
    }

    fun loadPastSessions(classId: String) {
        viewModelScope.launch {
            val list = repository.getPastSessionsForClass(classId)
            _pastSessions.value = list.sortedByDescending { SessionDates.toMillis(it.date) }
        }
    }

    fun loadLeaveRequests(classId: String) {
        viewModelScope.launch {
            try {
                _leaveRequests.value = LeaveRequestRules.sorted(leaveRepository.getForClass(classId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "Không tải được đơn xin nghỉ!")
            }
        }
    }

    fun decideLeaveRequest(request: LeaveRequest, approve: Boolean) {
        viewModelScope.launch {
            try {
                leaveRepository.decide(request, approve)
                loadLeaveRequests(request.classId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _operationResult.value = Pair(false, "Không lưu được quyết định. Vui lòng thử lại!")
            }
        }
    }
}
