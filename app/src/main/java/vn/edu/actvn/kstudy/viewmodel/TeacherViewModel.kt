package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.data.repository.TeacherRepository
import vn.edu.actvn.kstudy.util.SessionDates

class TeacherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TeacherRepository(application.applicationContext)

    private val _teacher = MutableLiveData<Teacher?>()
    val teacher: LiveData<Teacher?> = _teacher

    private val _classes = MutableLiveData<List<CourseClass>>()
    val classes: LiveData<List<CourseClass>> = _classes

    private val _classStudents = MutableLiveData<List<Student>>()
    val classStudents: LiveData<List<Student>> = _classStudents

    private val _activeSession = MutableLiveData<AttendanceSession?>()
    val activeSession: LiveData<AttendanceSession?> = _activeSession

    private val _records = MutableLiveData<List<AttendanceRecord>>()
    val records: LiveData<List<AttendanceRecord>> = _records

    private val _pastSessions = MutableLiveData<List<AttendanceSession>>()
    val pastSessions: LiveData<List<AttendanceSession>> = _pastSessions

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

    fun createSession(classId: String, qrCode: String) {
        if (qrCode.isBlank()) {
            _operationResult.value = Pair(false, "Mã QR không được trống!")
            return
        }
        viewModelScope.launch {
            val sessId = repository.createAttendanceSession(classId, qrCode)
            if (sessId.isNotEmpty()) {
                loadActiveSession(classId)
                _operationResult.value = Pair(true, "Mở phiên điểm danh thành công!")
            } else {
                _operationResult.value = Pair(false, "Mở phiên điểm danh thất bại!")
            }
        }
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
}
