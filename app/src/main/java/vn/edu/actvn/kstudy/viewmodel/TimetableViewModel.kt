package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.util.Timetable
import vn.edu.actvn.kstudy.util.TimetableDay
import vn.edu.actvn.kstudy.util.TimetableMode
import java.util.Calendar

sealed class TimetableState {
    object Loading : TimetableState()
    data class Ready(val timetable: Timetable) : TimetableState()
    data class Error(val message: String) : TimetableState()
}

// The timetable of a student (the classes they attend) or a teacher (the classes they teach), by day,
// week or month. It opens on the week of today; the user then moves from one period to the next.
class TimetableViewModel(
    private val dataSource: AttendanceDataSource,
    private val today: () -> Calendar = { Calendar.getInstance() }
) : ViewModel() {

    private val _state = MutableLiveData<TimetableState>(TimetableState.Loading)
    val state: LiveData<TimetableState> = _state

    private var classes: List<CourseClass>? = null
    private var mode = TimetableMode.WEEK

    // The day the shown period is built around; null until the user moves away from today
    private var day: Calendar? = null

    fun load(userId: String, role: Role) {
        _state.value = TimetableState.Loading
        viewModelScope.launch {
            try {
                classes = if (role == Role.TEACHER) {
                    dataSource.getClassesForTeacher(userId)
                } else {
                    dataSource.getClassesForStudent(userId)
                }
                show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TimetableState.Error(LOAD_FAILED)
            }
        }
    }

    // Shows the day, week or month that contains the day the user was on
    fun setMode(mode: TimetableMode) {
        if (mode == this.mode) return
        this.mode = mode
        show()
    }

    fun next() = move(1)

    fun previous() = move(-1)

    fun goToToday() {
        day = null
        show()
    }

    // A day picked in the month: its lessons are listed under the month
    fun selectDay(selected: TimetableDay) {
        day = (today().clone() as Calendar).apply {
            set(selected.year, selected.month - 1, selected.dayOfMonth)
        }
        show()
    }

    private fun move(steps: Int) {
        val moved = (currentDay().clone() as Calendar)
        when (mode) {
            TimetableMode.DAY -> moved.add(Calendar.DAY_OF_YEAR, steps)
            TimetableMode.WEEK -> moved.add(Calendar.DAY_OF_YEAR, 7 * steps)
            // 31/01 becomes 28/02 or 29/02: Calendar keeps the day inside the month
            TimetableMode.MONTH -> moved.add(Calendar.MONTH, steps)
        }
        day = moved
        show()
    }

    private fun currentDay(): Calendar = day ?: today()

    // Nothing to lay out before the classes have arrived
    private fun show() {
        val classes = classes ?: return
        _state.value = TimetableState.Ready(ScheduleUtils.timetable(classes, mode, currentDay(), today()))
    }

    companion object {
        const val LOAD_FAILED = "Không tải được thời khóa biểu. Vui lòng kiểm tra kết nối mạng!"
    }
}
