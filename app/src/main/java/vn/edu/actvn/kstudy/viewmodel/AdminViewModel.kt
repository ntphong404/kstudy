package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.*
import vn.edu.actvn.kstudy.data.repository.AdminRepository
import vn.edu.actvn.kstudy.util.PasswordRules
import vn.edu.actvn.kstudy.util.ProfileRules

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AdminRepository(application.applicationContext)

    private val _teachers = MutableLiveData<List<Teacher>>()
    val teachers: LiveData<List<Teacher>> = _teachers

    private val _students = MutableLiveData<List<Student>>()
    val students: LiveData<List<Student>> = _students

    private val _classes = MutableLiveData<List<CourseClass>>()
    val classes: LiveData<List<CourseClass>> = _classes

    private val _classStudents = MutableLiveData<List<Student>>()
    val classStudents: LiveData<List<Student>> = _classStudents

    private val _nonClassStudents = MutableLiveData<List<Student>>()
    val nonClassStudents: LiveData<List<Student>> = _nonClassStudents

    private val _stats = MutableLiveData<Map<String, Any>>()
    val stats: LiveData<Map<String, Any>> = _stats

    private val _activeSessions = MutableLiveData<List<Pair<String, String>>>()
    val activeSessions: LiveData<List<Pair<String, String>>> = _activeSessions

    private val _operationResult = MutableLiveData<Pair<Boolean, String>>()
    val operationResult: LiveData<Pair<Boolean, String>> = _operationResult

    fun loadDashboardStats() {
        viewModelScope.launch {
            _stats.value = repository.getSystemStats()
        }
    }

    fun loadActiveSessions() {
        viewModelScope.launch {
            _activeSessions.value = repository.getActiveSessionsOverview()
        }
    }

    // Reports an email that is not an address; the forms mark it at the field before it gets here
    private fun hasInvalidEmail(personal: PersonalInfo): Boolean {
        val invalid = !ProfileRules.isValidEmail(personal.email)
        if (invalid) _operationResult.value = Pair(false, ProfileRules.INVALID_EMAIL)
        return invalid
    }

    // --- ACCOUNTS ---

    // personName only appears in the message; the dialog marks a password that breaks the rules
    // at the field before it gets here
    fun resetPassword(userId: String, personName: String, newPassword: String) {
        val problem = PasswordRules.problemWithNew(newPassword)
        if (problem != null) {
            _operationResult.value = Pair(false, problem)
            return
        }
        viewModelScope.launch {
            val ok = repository.resetPassword(userId, newPassword)
            _operationResult.value = if (ok) {
                Pair(true, "Đã đặt lại mật khẩu cho $personName!")
            } else {
                Pair(false, "Đặt lại mật khẩu thất bại!")
            }
        }
    }

    // --- TEACHERS ---

    fun loadTeachers() {
        viewModelScope.launch {
            _teachers.value = repository.getTeachers()
        }
    }

    fun addTeacher(username: String, pass: String, teacher: Teacher) {
        if (username.isBlank() || pass.isBlank() || teacher.name.isBlank() || teacher.subject.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        if (hasInvalidEmail(teacher.personal)) return
        viewModelScope.launch {
            val ok = repository.addTeacher(username, pass, teacher)
            if (ok) {
                loadTeachers()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Thêm giáo viên thành công!")
            } else {
                _operationResult.value = Pair(false, "Tên tài khoản đã tồn tại hoặc có lỗi!")
            }
        }
    }

    fun updateTeacher(teacher: Teacher) {
        if (teacher.name.isBlank() || teacher.subject.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        if (hasInvalidEmail(teacher.personal)) return
        viewModelScope.launch {
            val ok = repository.updateTeacher(teacher)
            if (ok) {
                loadTeachers()
                _operationResult.value = Pair(true, "Cập nhật thành công!")
            } else {
                _operationResult.value = Pair(false, "Cập nhật thất bại!")
            }
        }
    }

    fun deleteTeacher(userId: String) {
        viewModelScope.launch {
            val ok = repository.deleteTeacher(userId)
            if (ok) {
                loadTeachers()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Xóa giáo viên thành công!")
            } else {
                _operationResult.value = Pair(false, "Xóa thất bại!")
            }
        }
    }

    // --- STUDENTS ---

    fun loadStudents() {
        viewModelScope.launch {
            _students.value = repository.getStudents()
        }
    }

    fun addStudent(username: String, pass: String, student: Student) {
        if (username.isBlank() || pass.isBlank() || student.name.isBlank() || student.studentCode.isBlank() || student.className.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        if (hasInvalidEmail(student.personal)) return
        viewModelScope.launch {
            val ok = repository.addStudent(username, pass, student)
            if (ok) {
                loadStudents()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Thêm học sinh thành công!")
            } else {
                _operationResult.value = Pair(false, "Tài khoản hoặc mã số học sinh đã tồn tại!")
            }
        }
    }

    fun updateStudent(student: Student) {
        if (student.name.isBlank() || student.studentCode.isBlank() || student.className.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        if (hasInvalidEmail(student.personal)) return
        viewModelScope.launch {
            val ok = repository.updateStudent(student)
            if (ok) {
                loadStudents()
                _operationResult.value = Pair(true, "Cập nhật thành công!")
            } else {
                _operationResult.value = Pair(false, "Cập nhật thất bại!")
            }
        }
    }

    fun deleteStudent(userId: String) {
        viewModelScope.launch {
            val ok = repository.deleteStudent(userId)
            if (ok) {
                loadStudents()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Xóa học sinh thành công!")
            } else {
                _operationResult.value = Pair(false, "Xóa thất bại!")
            }
        }
    }

    // --- CLASSES ---

    fun loadClasses() {
        viewModelScope.launch {
            _classes.value = repository.getClasses()
        }
    }

    // startDate, endDate: "dd/MM/yyyy", or blank for a class without such a date
    fun addClass(
        name: String, academicYear: String, teacherId: String, room: String, schedule: String,
        startDate: String, endDate: String
    ) {
        if (name.isBlank() || academicYear.isBlank() || teacherId.isBlank() || room.isBlank() || schedule.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            val ok = repository.addClass(name, academicYear, teacherId, room, schedule, startDate, endDate)
            if (ok) {
                loadClasses()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Thêm lớp học thành công!")
            } else {
                _operationResult.value = Pair(false, "Thêm thất bại!")
            }
        }
    }

    fun updateClass(
        classId: String, name: String, academicYear: String, teacherId: String, room: String, schedule: String,
        startDate: String, endDate: String
    ) {
        if (name.isBlank() || academicYear.isBlank() || teacherId.isBlank() || room.isBlank() || schedule.isBlank()) {
            _operationResult.value = Pair(false, "Vui lòng nhập đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            val ok = repository.updateClass(classId, name, academicYear, teacherId, room, schedule, startDate, endDate)
            if (ok) {
                loadClasses()
                _operationResult.value = Pair(true, "Cập nhật lớp học thành công!")
            } else {
                _operationResult.value = Pair(false, "Cập nhật thất bại!")
            }
        }
    }

    fun deleteClass(classId: String) {
        viewModelScope.launch {
            val ok = repository.deleteClass(classId)
            if (ok) {
                loadClasses()
                loadDashboardStats()
                _operationResult.value = Pair(true, "Xóa lớp học thành công!")
            } else {
                _operationResult.value = Pair(false, "Xóa thất bại!")
            }
        }
    }

    // --- ENROLLMENTS ---

    fun loadEnrollmentStudents(classId: String) {
        viewModelScope.launch {
            _classStudents.value = repository.getStudentsInClass(classId)
            _nonClassStudents.value = repository.getStudentsNotInClass(classId)
        }
    }

    fun enrollStudent(classId: String, studentId: String) {
        viewModelScope.launch {
            val ok = repository.enrollStudent(classId, studentId)
            if (ok) {
                loadEnrollmentStudents(classId)
                loadClasses()
                _operationResult.value = Pair(true, "Thêm học sinh vào lớp thành công!")
            } else {
                _operationResult.value = Pair(false, "Không thể thêm học sinh!")
            }
        }
    }

    fun unenrollStudent(classId: String, studentId: String) {
        viewModelScope.launch {
            val ok = repository.unenrollStudent(classId, studentId)
            if (ok) {
                loadEnrollmentStudents(classId)
                loadClasses()
                _operationResult.value = Pair(true, "Xóa học sinh khỏi lớp thành công!")
            } else {
                _operationResult.value = Pair(false, "Không thể xóa học sinh!")
            }
        }
    }
}
