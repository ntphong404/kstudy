package vn.edu.actvn.kstudy.testutil

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import vn.edu.actvn.kstudy.FragmentHostActivity
import java.io.File

object UiTestUtils {

    fun launchFragment(create: () -> Fragment): ActivityScenario<FragmentHostActivity> {
        val scenario = ActivityScenario.launch(FragmentHostActivity::class.java)
        scenario.onActivity { it.show(create()) }
        return scenario
    }

    // The screens update from coroutines, a moment after the action that triggered them:
    // retry the check until it passes or the time is up.
    fun waitUntil(timeoutMs: Long = 5000, check: () -> Unit) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            try {
                check()
                return
            } catch (e: Throwable) {
                if (SystemClock.uptimeMillis() > deadline) throw e
                Thread.sleep(100)
            }
        }
    }

    // Espresso's own scrollTo() refuses views inside a NestedScrollView
    fun nestedScrollTo(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = allOf(
            isDescendantOfA(isAssignableFrom(NestedScrollView::class.java)),
            withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)
        )

        override fun getDescription(): String = "scroll the NestedScrollView until the view is visible"

        override fun perform(uiController: UiController, view: View) {
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
            uiController.loopMainThreadUntilIdle()
        }
    }

    // Saved under Android/data/vn.edu.actvn.kstudy/files/screenshots on the device,
    // handy for checking the layout by eye or for illustrating a report.
    fun saveScreenshot(name: String) {
        Thread.sleep(900) // let the chart animations finish
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
