package vn.edu.actvn.kstudy.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.testutil.FakeAttendanceDataSource
import vn.edu.actvn.kstudy.testutil.MainDispatcherRule
import vn.edu.actvn.kstudy.testutil.TestData
import java.io.IOException
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // c1: teacher t1, students s1-s3, "Thứ 2 (07:30-09:00)"; c2: teacher t2, student s3, "Thứ 5 (13:30-15:30)"
    private val dataSource = FakeAttendanceDataSource(listOf(TestData.sampleClass(), TestData.emptyClass()))

    // 14/10/2026 is a Wednesday
    private val today: Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 14, 10, 0)
    }
    private val viewModel = TimetableViewModel(dataSource) { today }

    private fun ready() = (viewModel.state.value as TimetableState.Ready).timetable

    @Test
    fun state_beforeAnythingIsLoaded_isLoading() {
        assertEquals(TimetableState.Loading, viewModel.state.value)
    }

    @Test
    fun load_student_showsTheClassesTheyAttend() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.load("s3", Role.STUDENT)
        advanceUntilIdle()

        val timetable = ready()
        assertEquals(listOf("07:30-09:00", "13:30-15:30"), timetable.slots)
        assertEquals(listOf("Toán cao cấp A1"), timetable.days[0].lessons.map { it.courseClass.name })
        assertEquals(listOf("Anh văn B1"), timetable.days[3].lessons.map { it.courseClass.name })
        assertEquals(listOf("student:s3"), dataSource.calls)
    }

    @Test
    fun load_teacher_showsOnlyTheClassesTheyTeach() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.load("t1", Role.TEACHER)
        advanceUntilIdle()

        val timetable = ready()
        assertEquals(listOf("07:30-09:00"), timetable.slots)
        assertEquals(
            listOf("Toán cao cấp A1"),
            timetable.days.flatMap { it.lessons }.map { it.courseClass.name }
        )
        assertEquals(listOf("teacher:t1"), dataSource.calls)
    }

    @Test
    fun load_usesTheWeekOfToday() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.load("s3", Role.STUDENT)
        advanceUntilIdle()

        val timetable = ready()
        assertEquals("12/10", timetable.days.first().date)
        assertEquals("18/10", timetable.days.last().date)
        assertEquals(listOf("Thứ 4"), timetable.days.filter { it.isToday }.map { it.name })
    }

    @Test
    fun load_userWithoutClasses_givesAnEmptyTimetable() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.load("nobody", Role.STUDENT)
        advanceUntilIdle()

        assertTrue(ready().isEmpty)
    }

    @Test
    fun load_whenTheDataCannotBeRead_reportsAnError() = runTest(mainDispatcherRule.dispatcher) {
        dataSource.failure = IOException("no network")

        viewModel.load("s3", Role.STUDENT)
        advanceUntilIdle()

        assertEquals(TimetableState.Error(TimetableViewModel.LOAD_FAILED), viewModel.state.value)
    }
}
