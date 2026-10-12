package vn.edu.actvn.kstudy.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.data.ai.AiTurn
import vn.edu.actvn.kstudy.data.model.ChatMessage
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.testutil.FakeAiService
import vn.edu.actvn.kstudy.testutil.FakeAttendanceDataSource
import vn.edu.actvn.kstudy.testutil.MainDispatcherRule
import vn.edu.actvn.kstudy.testutil.TestData
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dataSource = FakeAttendanceDataSource(listOf(TestData.sampleClass(), TestData.emptyClass()))
    private val ai = FakeAiService()
    private val viewModel = AiChatViewModel(dataSource, ai, today = { "Chủ Nhật, 11/10/2026" })

    private val messages: List<ChatMessage> get() = viewModel.messages.value!!

    private fun startAsStudent() = viewModel.start("s3", Role.STUDENT, "Học sinh 3")

    @Test
    fun start_showsWelcomeMessageWithTheUsersName() {
        startAsStudent()

        assertEquals(1, messages.size)
        assertFalse(messages[0].fromUser)
        assertFalse(messages[0].isError)
        assertTrue(messages[0].text.startsWith("Xin chào Học sinh 3!"))
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun start_publishesTheSuggestedQuestionsOfTheRole() {
        assertTrue(viewModel.suggestions.value!!.isEmpty())

        startAsStudent()
        assertEquals(AiChatViewModel.suggestionsFor(Role.STUDENT), viewModel.suggestions.value)

        viewModel.start("t1", Role.TEACHER, "Nguyễn Văn An")
        assertEquals(AiChatViewModel.suggestionsFor(Role.TEACHER), viewModel.suggestions.value)
    }

    @Test
    fun start_calledAgainForTheSameUser_keepsTheConversation() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        startAsStudent()

        assertEquals(3, messages.size)
    }

    @Test
    fun start_forAnotherUser_resetsTheConversation() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        viewModel.start("t1", Role.TEACHER, "Nguyễn Văn An")
        viewModel.sendMessage("Ai vắng nhiều nhất?")
        advanceUntilIdle()

        assertTrue(messages[0].text.startsWith("Xin chào Nguyễn Văn An!"))
        assertEquals(3, messages.size)
        assertTrue("history of the previous user must not be sent", ai.requests.last().history.isEmpty())
    }

    @Test
    fun sendMessage_appendsQuestionAndAnswer() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        viewModel.sendMessage("  Em vắng mấy buổi?  ")
        advanceUntilIdle()

        assertEquals(3, messages.size)
        assertEquals(ChatMessage("Em vắng mấy buổi?", fromUser = true), messages[1])
        assertEquals(ChatMessage("Trả lời cho: Em vắng mấy buổi?", fromUser = false), messages[2])
        assertEquals(false, viewModel.isSending.value)
    }

    @Test
    fun sendMessage_showsQuestionImmediatelyAndMarksAsSending() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        viewModel.sendMessage("Em vắng mấy buổi?")

        assertEquals("the question is visible before the AI answers", 2, messages.size)
        assertEquals(true, viewModel.isSending.value)

        advanceUntilIdle()
        assertEquals(false, viewModel.isSending.value)
    }

    @Test
    fun sendMessage_whileWaitingForAnAnswer_isRejected() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        val first = viewModel.sendMessage("Câu 1")
        val second = viewModel.sendMessage("Câu 2")
        advanceUntilIdle()

        assertTrue(first)
        assertFalse("the screen must be told that the second question was not sent", second)
        assertEquals(listOf("Câu 1"), ai.requests.map { it.message })
        assertEquals(3, messages.size)
    }

    @Test
    fun sendMessage_blankText_isRejected() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        val accepted = viewModel.sendMessage("   ")
        advanceUntilIdle()

        assertFalse(accepted)
        assertEquals(1, messages.size)
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun sendMessage_beforeTheUserIsKnown_isRejectedSoTheTextIsNotLost() = runTest(mainDispatcherRule.dispatcher) {
        val accepted = viewModel.sendMessage("Xin chào")
        advanceUntilIdle()

        assertFalse(accepted)
        assertTrue(messages.isEmpty())
        assertTrue(ai.requests.isEmpty())
    }

    @Test
    fun sendMessage_onceTheUserIsKnown_acceptsTheQuestionThatWasRejectedBefore() =
        runTest(mainDispatcherRule.dispatcher) {
            assertFalse(viewModel.sendMessage("Em vắng mấy buổi?"))

            startAsStudent()
            val accepted = viewModel.sendMessage("Em vắng mấy buổi?")
            advanceUntilIdle()

            assertTrue(accepted)
            assertEquals("Trả lời cho: Em vắng mấy buổi?", messages.last().text)
        }

    @Test
    fun isReady_turnsTrueOnlyWhenARealUserStartsTheChat() {
        assertEquals(false, viewModel.isReady.value)

        viewModel.start("", Role.STUDENT, "")
        assertEquals("an empty user id means the profile is not loaded yet", false, viewModel.isReady.value)

        startAsStudent()
        assertEquals(true, viewModel.isReady.value)
    }

    @Test
    fun sendMessage_asStudent_sendsOnlyThatStudentsData() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        val instruction = ai.requests.single().systemInstruction
        assertEquals(listOf("student:s3"), dataSource.calls)
        assertTrue(instruction.contains("học sinh Học sinh 3"))
        assertTrue(instruction.contains("Hôm nay là Chủ Nhật, 11/10/2026"))
        assertTrue(instruction.contains("có mặt 2, muộn 1, vắng 2, tỷ lệ đi học 60%"))
        assertFalse(instruction.contains("Học sinh 1"))
        assertFalse(instruction.contains("Học sinh 2"))
    }

    @Test
    fun sendMessage_asTeacher_sendsTheTeachersClasses() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.start("t1", Role.TEACHER, "Nguyễn Văn An")

        viewModel.sendMessage("Ai vắng nhiều nhất?")
        advanceUntilIdle()

        val instruction = ai.requests.single().systemInstruction
        assertEquals(listOf("teacher:t1"), dataSource.calls)
        assertTrue(instruction.contains("giáo viên Nguyễn Văn An"))
        assertTrue(instruction.contains("Số lớp đang dạy: 1"))
        assertTrue(instruction.contains("SV003 Học sinh 3: có mặt 2, muộn 1, vắng 2"))
        assertFalse("classes of other teachers are not included", instruction.contains("Anh văn B1"))
    }

    @Test
    fun sendMessage_asAdmin_sendsTheWholeSystem() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.start("a1", Role.ADMIN, "Admin")

        viewModel.sendMessage("Tóm tắt")
        advanceUntilIdle()

        val instruction = ai.requests.single().systemInstruction
        assertEquals(listOf("all"), dataSource.calls)
        assertTrue(instruction.contains("Toàn hệ thống: 2 lớp học."))
    }

    @Test
    fun sendMessage_reloadsDataForEveryQuestion() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        viewModel.sendMessage("Câu 1")
        advanceUntilIdle()

        // The teacher closes another session in which s3 was absent
        val sample = TestData.sampleClass()
        dataSource.classes = listOf(
            sample.copy(
                sessions = sample.sessions + TestData.session("d6", "05/10/2026 07:30"),
                records = sample.records + TestData.record("d6", "s3", "ABSENT")
            )
        )
        viewModel.sendMessage("Câu 2")
        advanceUntilIdle()

        assertEquals(listOf("student:s3", "student:s3"), dataSource.calls)
        assertTrue(ai.requests[1].systemInstruction.contains("có mặt 2, muộn 1, vắng 3, tỷ lệ đi học 50%"))
    }

    @Test
    fun sendMessage_passesEarlierTurnsAsHistory() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        viewModel.sendMessage("Câu 1")
        advanceUntilIdle()
        viewModel.sendMessage("Câu 2")
        advanceUntilIdle()

        assertTrue(ai.requests[0].history.isEmpty())
        assertEquals(listOf(AiTurn("Câu 1", "Trả lời cho: Câu 1")), ai.requests[1].history)
        assertEquals("Câu 2", ai.requests[1].message)
    }

    @Test
    fun sendMessage_limitsTheHistorySentToTheModel() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()

        repeat(AiChatViewModel.MAX_HISTORY_TURNS + 3) { index ->
            viewModel.sendMessage("Câu $index")
            advanceUntilIdle()
        }

        val lastHistory = ai.requests.last().history
        assertEquals(AiChatViewModel.MAX_HISTORY_TURNS, lastHistory.size)
        assertEquals("Câu ${AiChatViewModel.MAX_HISTORY_TURNS + 1}", lastHistory.last().question)
    }

    @Test
    fun sendMessage_stripsMarkdownFromTheAnswer() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        ai.reply = { "**Bạn** đã vắng 2 buổi:\n* 01/09\n* 08/09" }

        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        assertEquals("Bạn đã vắng 2 buổi:\n- 01/09\n- 08/09", messages.last().text)
    }

    @Test
    fun sendMessage_whenAiFails_showsItsMessageAsAnError() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        ai.failure = AiServiceException("Không có kết nối mạng.")

        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        assertEquals(ChatMessage("Không có kết nối mạng.", fromUser = false, isError = true), messages.last())
        assertEquals(false, viewModel.isSending.value)
    }

    @Test
    fun sendMessage_afterAFailure_doesNotReplayTheFailedQuestion() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        ai.failure = AiServiceException("lỗi")
        viewModel.sendMessage("Câu lỗi")
        advanceUntilIdle()

        ai.failure = null
        viewModel.sendMessage("Câu sau")
        advanceUntilIdle()

        assertTrue(ai.requests.last().history.isEmpty())
        assertEquals("Trả lời cho: Câu sau", messages.last().text)
        assertFalse(messages.last().isError)
    }

    @Test
    fun sendMessage_whenDataCannotBeLoaded_showsErrorWithoutCallingAi() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        dataSource.failure = IOException("offline")

        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        assertEquals(ChatMessage(AiChatViewModel.DATA_FAILED, fromUser = false, isError = true), messages.last())
        assertTrue(ai.requests.isEmpty())
        assertEquals(false, viewModel.isSending.value)
    }

    @Test
    fun sendMessage_whenAiReturnsNothing_showsError() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        ai.reply = { "   " }

        viewModel.sendMessage("Em vắng mấy buổi?")
        advanceUntilIdle()

        assertEquals(ChatMessage(AiChatViewModel.EMPTY_ANSWER, fromUser = false, isError = true), messages.last())
    }

    @Test
    fun clearConversation_goesBackToTheWelcomeMessageAndForgetsHistory() = runTest(mainDispatcherRule.dispatcher) {
        startAsStudent()
        viewModel.sendMessage("Câu 1")
        advanceUntilIdle()

        viewModel.clearConversation()
        viewModel.sendMessage("Câu 2")
        advanceUntilIdle()

        assertEquals(3, messages.size)
        assertTrue(messages[0].text.startsWith("Xin chào Học sinh 3!"))
        assertTrue(ai.requests.last().history.isEmpty())
    }

    @Test
    fun suggestions_existForEveryRole() {
        for (role in Role.values()) {
            val suggestions = AiChatViewModel.suggestionsFor(role)
            assertTrue(suggestions.size >= 3)
            assertTrue(suggestions.all { it.isNotBlank() })
        }
    }
}
