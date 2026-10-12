package vn.edu.actvn.kstudy.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.startsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.actvn.kstudy.FragmentHostActivity
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.testutil.UiTestUtils.launchFragment
import vn.edu.actvn.kstudy.testutil.UiTestUtils.saveScreenshot
import vn.edu.actvn.kstudy.testutil.UiTestUtils.waitUntil
import vn.edu.actvn.kstudy.ui.teacher.TeacherQrCodeFragment
import vn.edu.actvn.kstudy.util.RotatingCode

// Runs the real QR screen of the teacher on a device. The session id is left empty, which makes
// the screen skip loading the attendance list, so nothing here needs Firestore.
@RunWith(AndroidJUnit4::class)
class TeacherQrCodeFragmentTest {

    private val secret = "kstudy-demo-secret"

    private fun launch(
        checksLocation: Boolean = true,
        lateAfterMinutes: Int = 10
    ): ActivityScenario<FragmentHostActivity> = launchFragment {
        TeacherQrCodeFragment.newInstance(
            sessionId = "",
            classId = "c1",
            className = "Toán cao cấp A1",
            academicYear = "2025-2026",
            secret = secret,
            checksLocation = checksLocation,
            lateAfterMinutes = lateAfterMinutes
        )
    }

    private fun ActivityScenario<FragmentHostActivity>.shownCode(): String {
        var code = ""
        onActivity { code = it.findViewById<TextView>(R.id.tvManualCode).text.toString() }
        return code
    }

    // The code a student's phone would accept right now, computed the way the student's phone does it
    private fun assertAcceptedNow(code: String) {
        assertTrue("\"$code\" is not valid at this moment", RotatingCode.matches(secret, code, System.currentTimeMillis()))
    }

    @Test
    fun showsTheCodeOfTheCurrentMoment() {
        launch().use { scenario ->
            waitUntil { assertEquals(RotatingCode.codeAt(secret, System.currentTimeMillis()), scenario.shownCode()) }

            onView(withId(R.id.tvQrClassTitle)).check(matches(withText("Toán cao cấp A1 (2025-2026)")))
            onView(withId(R.id.qrView)).check(matches(isDisplayed()))
            onView(withId(R.id.tvQrCountdown)).check(matches(withText(startsWith("Tự đổi sau "))))
            saveScreenshot("qr_1_rotating_code")
        }
    }

    @Test
    fun replacesTheCodeByItselfWhenTheWindowEnds() {
        launch().use { scenario ->
            waitUntil { assertAcceptedNow(scenario.shownCode()) }
            val first = scenario.shownCode()

            // One full window plus a margin
            waitUntil(timeoutMs = (RotatingCode.STEP_SECONDS + 3) * 1000L) {
                assertNotEquals(first, scenario.shownCode())
            }

            assertEquals(RotatingCode.codeAt(secret, System.currentTimeMillis()), scenario.shownCode())
        }
    }

    @Test
    fun fullScreenDialog_showsTheSameRotatingCode() {
        launch().use { scenario ->
            waitUntil { assertAcceptedNow(scenario.shownCode()) }

            onView(withId(R.id.btnZoomQr)).perform(click())

            waitUntil {
                onView(withId(R.id.dialogTvCode)).inRoot(isDialog()).check(matches(withText(scenario.shownCode())))
            }
            onView(withId(R.id.dialogQrView)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.dialogTvCountdown)).inRoot(isDialog()).check(matches(withText(startsWith("Tự đổi sau "))))
            saveScreenshot("qr_2_full_screen")
            pressBack()
        }
    }

    @Test
    fun tellsWhichChecksTheSessionApplies() {
        launch(checksLocation = true, lateAfterMinutes = 10).use {
            onView(withId(R.id.tvQrRules))
                .check(matches(withText("Vị trí: trong bán kính 100 m • Đi muộn: quét sau 10 phút")))
        }
    }

    @Test
    fun sessionOpenedWithoutLocation_saysThatLocationIsNotChecked() {
        launch(checksLocation = false, lateAfterMinutes = 5).use {
            onView(withId(R.id.tvQrRules)).check(
                matches(withText("Vị trí: không kiểm tra (chưa lấy được vị trí của giáo viên) • Đi muộn: quét sau 5 phút"))
            )
        }
    }
}
