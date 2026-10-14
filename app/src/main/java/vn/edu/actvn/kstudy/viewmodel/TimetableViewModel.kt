package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.util.WeekTimetable
import java.util.Calendar

sealed class TimetableState {
    object Loading : TimetableState()
    data class Ready(val timetable: WeekTimetable) : TimetableState()
    data class Error(val message: String) : TimetableState()
}

// The weekly timetable of a student (the classes they attend) or a teacher (the classes they teach)
class TimetableViewModel(
    private val dataSource: AttendanceDataSource,
    private val today: () -> Calendar = { Calendar.getInstance() }
) : ViewModel() {

    private val _state = MutableLiveData<TimetableState>(TimetableState.Loading)
    val state: LiveData<TimetableState> = _state

    fun load(userId: String, role: Role) {
        _state.value = TimetableState.Loading
        viewModelScope.launch {
            _state.value = try {
                val classes = if (role == Role.TEACHER) {
                    dataSource.getClassesForTeacher(userId)
                } else {
                    dataSource.getClassesForStudent(userId)
                }
                TimetableState.Ready(ScheduleUtils.weekTimetable(classes, today()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TimetableState.Error(LOAD_FAILED)
            }
        }
    }

    companion object {
        const val LOAD_FAILED = "Không tải được thời khóa biểu. Vui lòng kiểm tra kết nối mạng!"
    }
}
