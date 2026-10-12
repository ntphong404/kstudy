package vn.edu.actvn.kstudy.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.actvn.kstudy.FragmentHostActivity
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.data.model.LeaveRequest
import vn.edu.actvn.kstudy.data.model.LeaveStatus
import vn.edu.actvn.kstudy.testutil.UiTestUtils.saveScreenshot
import vn.edu.actvn.kstudy.ui.adapter.AttendanceRecordAdapter
import vn.edu.actvn.kstudy.ui.common.AttendanceViews

// The leave request cards and the four-status attendance row, drawn on a device without any data source
@RunWith(AndroidJUnit4::class)
class AttendanceViewsTest {

    private val pending = LeaveRequest(
        id = "r1",
        classId = "c1",
        studentId = "s3",
        studentName = "Học sinh 3",
        studentCode = "SV003",
        date = "12/10/2026",
        reason = "Em bị sốt, có giấy khám bệnh",
        status = LeaveStatus.PENDING
    )

    private fun launchWith(fill: (LinearLayout) -> Unit): ActivityScenario<FragmentHostActivity> {
        val scenario = ActivityScenario.launch(FragmentHostActivity::class.java)
        scenario.onActivity { activity ->
            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                val padding = (16 * resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            activity.findViewById<FrameLayout>(FragmentHostActivity.CONTAINER_ID).addView(
                list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            fill(list)
        }
        return scenario
    }

    @Test
    fun teacherSeesWhoAsksAndCanDecideAPendingRequest() {
        val decisions = mutableListOf<Boolean>()
        launchWith { list ->
            AttendanceViews.addLeaveRequest(list, pending, forTeacher = true) { decisions.add(it) }
        }.use {
            onView(withId(R.id.tvLeaveTitle)).check(matches(withText("Học sinh 3 (SV003)")))
            onView(withId(R.id.tvLeaveDate)).check(matches(withText("Xin nghỉ ngày 12/10/2026")))
            onView(withId(R.id.tvLeaveReason)).check(matches(withText("Lý do: Em bị sốt, có giấy khám bệnh")))
            onView(withId(R.id.tvLeaveStatus)).check(matches(withText("Chờ duyệt")))

            onView(withId(R.id.btnLeaveApprove)).check(matches(isDisplayed())).perform(click())
            onView(withId(R.id.btnLeaveReject)).perform(click())

            assertEquals(listOf(true, false), decisions)
        }
    }

    @Test
    fun decidedRequest_hasNoButtonsLeft() {
        launchWith { list ->
            AttendanceViews.addLeaveRequest(list, pending.copy(status = LeaveStatus.APPROVED), forTeacher = true) { }
        }.use {
            onView(withId(R.id.tvLeaveStatus)).check(matches(withText("Đã duyệt")))
            onView(allOf(withText("DUYỆT"), isDisplayed())).check(doesNotExist())
        }
    }

    @Test
    fun studentSeesOwnRequestsWithTheirStatusButCannotDecide() {
        launchWith { list ->
            AttendanceViews.addLeaveRequest(list, pending, forTeacher = false)
            AttendanceViews.addLeaveRequest(
                list, pending.copy(id = "r2", date = "05/10/2026", status = LeaveStatus.APPROVED), forTeacher = false
            )
            AttendanceViews.addLeaveRequest(
                list, pending.copy(id = "r3", date = "28/09/2026", status = LeaveStatus.REJECTED), forTeacher = false
            )
        }.use {
            onView(withText("Ngày 12/10/2026")).check(matches(isDisplayed()))
            onView(withText("Chờ duyệt")).check(matches(isDisplayed()))
            onView(withText("Đã duyệt")).check(matches(isDisplayed()))
            onView(withText("Từ chối")).check(matches(isDisplayed()))
            onView(allOf(withText("DUYỆT"), isDisplayed())).check(doesNotExist())
            onView(withText("Học sinh 3 (SV003)")).check(doesNotExist())
            saveScreenshot("leave_1_student_requests")
        }
    }

    @Test
    fun emptyNote_showsTheMessage() {
        launchWith { list ->
            AttendanceViews.addEmptyNote(list, "Chưa có đơn xin nghỉ nào.")
        }.use {
            onView(withText("Chưa có đơn xin nghỉ nào.")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun attendanceRow_offersFourStatusesIncludingExcused() {
        val changes = mutableListOf<String>()
        val record = AttendanceRecord(
            id = "rec1", sessionId = "d1", studentId = "s1",
            studentName = "Nguyễn Thị Thu Hằng", studentCode = "SV001", status = "ABSENT", timeMarked = "-"
        )
        launchWith { list ->
            val recycler = RecyclerView(list.context).apply {
                layoutManager = LinearLayoutManager(list.context)
                adapter = AttendanceRecordAdapter(
                    listOf(
                        record,
                        record.copy(id = "rec2", studentName = "Học sinh 2", studentCode = "SV002", status = "EXCUSED")
                    )
                ) { changed, status -> changes.add("${changed.id}:$status") }
            }
            list.addView(recycler)
        }.use { scenario ->
            saveScreenshot("leave_2_attendance_row")

            // Tap "Phép" on the first row and "Có" on the second, through the real buttons
            scenario.onActivity { activity ->
                val recycler = activity.findViewById<FrameLayout>(FragmentHostActivity.CONTAINER_ID)
                    .let { (it.getChildAt(0) as LinearLayout).getChildAt(0) as RecyclerView }
                recycler.getChildAt(0).findViewById<android.view.View>(R.id.btnStatusExcused).performClick()
                recycler.getChildAt(1).findViewById<android.view.View>(R.id.btnStatusPresent).performClick()
                // The status a row already has is not reported again
                recycler.getChildAt(1).findViewById<android.view.View>(R.id.btnStatusExcused).performClick()
            }

            assertEquals(listOf("rec1:EXCUSED", "rec2:PRESENT"), changes)
        }
    }
}
