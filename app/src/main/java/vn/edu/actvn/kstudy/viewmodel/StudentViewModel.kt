package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.data.repository.StudentRepository

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application.applicationContext)

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

    fun markAttendance(studentId: String, code: String) {
        if (code.isBlank()) {
            _attendanceResult.value = Pair(false, "Vui lòng nhập mã hoặc quét mã QR!")
            return
        }
        viewModelScope.launch {
            val res = repository.markAttendanceViaCode(studentId, code)
            _attendanceResult.value = res
            if (res.first) {
                loadClasses(studentId)
            }
        }
    }

    fun clearAttendanceResult() {
        _attendanceResult.value = null
    }
}
