package vn.edu.actvn.kstudy.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.actvn.kstudy.FragmentHostActivity
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.testutil.FakeAiService
import vn.edu.actvn.kstudy.testutil.FakeAttendanceDataSource
import vn.edu.actvn.kstudy.testutil.TestData
import vn.edu.actvn.kstudy.testutil.UiTestUtils.launchFragment
import vn.edu.actvn.kstudy.testutil.UiTestUtils.saveScreenshot
import vn.edu.actvn.kstudy.testutil.UiTestUtils.waitUntil
import vn.edu.actvn.kstudy.ui.common.AiAssistantFragment
import vn.edu.actvn.kstudy.viewmodel.AiChatViewModel
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory

// Runs the real AI assistant screen on a device with a scripted AI instead of Gemini
@RunWith(AndroidJUnit4::class)
class AiAssistantFragmentTest {

    private val dataSource = FakeAttendanceDataSource(listOf(TestData.sampleClass(), TestData.emptyClass()))
    private val ai = FakeAiService()

    @Before
    fun useFakes() {
        KstudyViewModelFactory.dataSourceProvider = { dataSource }
        KstudyViewModelFactory.aiServiceProvider = { ai }
    }

    @After
    fun restoreRealServices() {
        KstudyViewModelFactory.useRealServices()
    }

    // In the app the signed-in user comes from AuthViewModel (Firestore); here the chat is started directly
    private fun launchAsStudent(showBackButton: Boolean = false): ActivityScenario<FragmentHostActivity> {
        val scenario = launchFragment { AiAssistantFragment.newInstance(showBackButton) }
        scenario.onActivity { activity ->
            ViewModelProvider(activity, KstudyViewModelFactory)[AiChatViewModel::class.java]
                .start("s3", Role.STUDENT, "Học sinh 3")
        }
        waitUntil { onView(withText(startsWith("Xin chào Học sinh 3!"))).check(matches(isDisplayed())) }
        return scenario
    }

    private fun ask(question: String) {
        onView(withId(R.id.etAiMessage)).perform(replaceText(question), closeSoftKeyboard())
        onView(withId(R.id.btnSendAi)).perform(click())
    }

    @Test
    fun showsWelcomeMessageAndSuggestedQuestions() {
        launchAsStudent().use {
            onView(withText("Em đã vắng bao nhiêu buổi?")).check(matches(isDisplayed()))
            onView(withText("Em có nguy cơ bị cấm thi không?")).check(matches(isDisplayed()))
            onView(withId(R.id.btnBackAi)).check(matches(not(isDisplayed())))
            assertTrue("nothing is sent before the user asks", ai.requests.isEmpty())
            saveScreenshot("chat_1_welcome")
        }
    }

    @Test
    fun backButton_isShownWhenOpenedOnTopOfAnotherScreen() {
        launchAsStudent(showBackButton = true).use {
            onView(withId(R.id.btnBackAi)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun beforeTheUserIsLoaded_sendingIsBlockedAndTheTypedQuestionIsKept() {
        launchFragment { AiAssistantFragment.newInstance() }.use { scenario ->
            onView(withId(R.id.tvAiTyping)).check(matches(isDisplayed()))
            onView(withId(R.id.tvAiTyping)).check(matches(withText("Đang tải thông tin người dùng…")))
            onView(withId(R.id.btnSendAi)).check(matches(not(isEnabled())))

            onView(withId(R.id.etAiMessage)).perform(replaceText("Em vắng mấy buổi?"), closeSoftKeyboard())
            // A real tap is swallowed by the disabled button...
            onView(withId(R.id.btnSendAi)).perform(click())
            // ...and even if the click handler runs anyway, the question must not be thrown away
            scenario.onActivity { it.findViewById<View>(R.id.btnSendAi).performClick() }

            onView(withId(R.id.etAiMessage)).check(matches(withText("Em vắng mấy buổi?")))
            assertTrue(ai.requests.isEmpty())
            saveScreenshot("chat_5_loading_user")

            // The profile of the signed-in user arrives
            scenario.onActivity { activity ->
                ViewModelProvider(activity, KstudyViewModelFactory)[AiChatViewModel::class.java]
                    .start("s3", Role.STUDENT, "Học sinh 3")
            }
            waitUntil { onView(withId(R.id.btnSendAi)).check(matches(isEnabled())) }
            onView(withId(R.id.tvAiTyping)).check(matches(not(isDisplayed())))

            onView(withId(R.id.btnSendAi)).perform(click())

            waitUntil { onView(withText("Trả lời cho: Em vắng mấy buổi?")).check(matches(isDisplayed())) }
            onView(withId(R.id.etAiMessage)).check(matches(withText("")))
        }
    }

    @Test
    fun sendingAQuestion_showsQuestionAndAnswerAndClearsTheInput() {
        val answer = "Bạn đã vắng 2/5 buổi (40%) ở lớp Toán cao cấp A1, vượt ngưỡng 20% nên có nguy cơ bị cấm thi."
        ai.reply = { answer }
        launchAsStudent().use {
            ask("Em vắng mấy buổi?")

            waitUntil { onView(withText(answer)).check(matches(isDisplayed())) }
            onView(withText("Em vắng mấy buổi?")).check(matches(isDisplayed()))
            onView(withId(R.id.etAiMessage)).check(matches(withText("")))
            onView(withId(R.id.tvAiTyping)).check(matches(not(isDisplayed())))

            val request = ai.requests.single()
            assertEquals("Em vắng mấy buổi?", request.message)
            assertTrue(request.systemInstruction.contains("có mặt 2, muộn 1, vắng 2, tỷ lệ đi học 60%"))
            saveScreenshot("chat_2_answer")
        }
    }

    @Test
    fun tappingASuggestion_sendsThatQuestion() {
        launchAsStudent().use {
            onView(withText("Em có nguy cơ bị cấm thi không?")).perform(click())

            waitUntil {
                onView(withText("Trả lời cho: Em có nguy cơ bị cấm thi không?")).check(matches(isDisplayed()))
            }
            assertEquals("Em có nguy cơ bị cấm thi không?", ai.requests.single().message)
        }
    }

    @Test
    fun aiFailure_isShownInTheConversation() {
        val message = "Trợ lý AI đang quá tải (hết lượt gọi miễn phí trong phút này). Vui lòng thử lại sau ít phút."
        ai.failure = AiServiceException(message)
        launchAsStudent().use {
            ask("Em vắng mấy buổi?")

            waitUntil { onView(withText(message)).check(matches(isDisplayed())) }
            saveScreenshot("chat_3_error")
        }
    }

    @Test
    fun clearButton_startsANewConversation() {
        launchAsStudent().use {
            ask("Câu hỏi cũ")
            waitUntil { onView(withText("Trả lời cho: Câu hỏi cũ")).check(matches(isDisplayed())) }

            onView(withId(R.id.btnClearChat)).perform(click())

            waitUntil { onView(withText("Trả lời cho: Câu hỏi cũ")).check(doesNotExist()) }
            onView(withText("Câu hỏi cũ")).check(doesNotExist())
            onView(withText(startsWith("Xin chào Học sinh 3!"))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun conversation_survivesReopeningTheScreen() {
        launchAsStudent().use { scenario ->
            ask("Câu hỏi 1")
            waitUntil { onView(withText("Trả lời cho: Câu hỏi 1")).check(matches(isDisplayed())) }

            // What happens when the user switches to another bottom tab and comes back
            scenario.onActivity { it.show(AiAssistantFragment.newInstance()) }

            waitUntil { onView(withText("Trả lời cho: Câu hỏi 1")).check(matches(isDisplayed())) }
            onView(withText("Câu hỏi 1")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun keyboard_doesNotCoverTheInputRow() {
        launchAsStudent().use { scenario ->
            onView(withId(R.id.etAiMessage)).perform(click())

            // [keyboard height, bottom edge of the send button, window height]
            fun measure(): IntArray {
                val result = IntArray(3)
                scenario.onActivity { activity ->
                    val send = activity.findViewById<View>(R.id.btnSendAi)
                    val root = send.rootView
                    val location = IntArray(2)
                    send.getLocationInWindow(location)
                    result[0] = ViewCompat.getRootWindowInsets(root)
                        ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    result[1] = location[1] + send.height
                    result[2] = root.height
                }
                return result
            }

            var keyboardShown = false
            try {
                waitUntil(timeoutMs = 8000) { assertTrue(measure()[0] > 0) }
                keyboardShown = true
            } catch (e: AssertionError) {
                // Devices with a hardware keyboard may never show the on-screen one
            }
            assumeTrue("the on-screen keyboard is not available on this device", keyboardShown)

            waitUntil {
                val (keyboardHeight, sendBottom, windowHeight) = measure()
                assertTrue(
                    "the send button (bottom=$sendBottom) is behind the keyboard (top=${windowHeight - keyboardHeight})",
                    sendBottom <= windowHeight - keyboardHeight
                )
            }
            saveScreenshot("chat_4_keyboard")
        }
    }
}
