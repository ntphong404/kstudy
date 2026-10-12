package vn.edu.actvn.kstudy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.ai.AiService
import vn.edu.actvn.kstudy.data.ai.AiServiceException
import vn.edu.actvn.kstudy.data.ai.AiTurn
import vn.edu.actvn.kstudy.data.model.ChatMessage
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.data.repository.AttendanceDataSource
import vn.edu.actvn.kstudy.util.AiPromptBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatViewModel(
    private val dataSource: AttendanceDataSource,
    private val aiService: AiService,
    private val today: () -> String = { SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date()) }
) : ViewModel() {

    private val _messages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isSending = MutableLiveData(false)
    val isSending: LiveData<Boolean> = _isSending

    // False until start() has received the signed-in user: no question can be asked before that
    private val _isReady = MutableLiveData(false)
    val isReady: LiveData<Boolean> = _isReady

    // Ready-made questions shown as chips, depending on the role of the user
    private val _suggestions = MutableLiveData<List<String>>(emptyList())
    val suggestions: LiveData<List<String>> = _suggestions

    // Only exchanges that got a real answer are replayed to the model
    private val turns = mutableListOf<AiTurn>()

    private var userId: String = ""
    private var role: Role = Role.STUDENT
    private var userName: String = ""

    // Safe to call every time the screen opens: the conversation is kept unless another account is used
    fun start(userId: String, role: Role, userName: String) {
        if (userId.isEmpty()) return
        this.userName = userName
        if (userId == this.userId) return
        this.userId = userId
        this.role = role
        turns.clear()
        _messages.value = listOf(ChatMessage(welcomeText(role, userName), fromUser = false))
        _suggestions.value = suggestionsFor(role)
        _isReady.value = true
    }

    // Returns false when the question was not accepted (blank, user not loaded yet, or an answer
    // is still pending), so the screen knows it has to keep what the user typed.
    fun sendMessage(text: String): Boolean {
        val question = text.trim()
        if (question.isEmpty() || userId.isEmpty() || _isSending.value == true) return false

        append(ChatMessage(question, fromUser = true))
        _isSending.value = true
        viewModelScope.launch {
            val reply = try {
                // Reloaded for every question so the answer reflects a session that was just closed
                val data = loadData()
                val instruction = AiPromptBuilder.chatSystemInstruction(role, userName, today(), data)
                val answer = AiPromptBuilder.cleanReply(
                    aiService.generate(instruction, turns.takeLast(MAX_HISTORY_TURNS), question)
                )
                if (answer.isEmpty()) {
                    ChatMessage(EMPTY_ANSWER, fromUser = false, isError = true)
                } else {
                    turns.add(AiTurn(question, answer))
                    ChatMessage(answer, fromUser = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiServiceException) {
                ChatMessage(e.message ?: AI_FAILED, fromUser = false, isError = true)
            } catch (e: Exception) {
                ChatMessage(DATA_FAILED, fromUser = false, isError = true)
            }
            append(reply)
            _isSending.value = false
        }
        return true
    }

    fun clearConversation() {
        if (userId.isEmpty() || _isSending.value == true) return
        turns.clear()
        _messages.value = listOf(ChatMessage(welcomeText(role, userName), fromUser = false))
    }

    private suspend fun loadData(): String = when (role) {
        Role.STUDENT -> AiPromptBuilder.studentData(userId, dataSource.getAttendanceForStudent(userId))
        Role.TEACHER -> AiPromptBuilder.teacherData(dataSource.getAttendanceForTeacher(userId))
        Role.ADMIN -> AiPromptBuilder.adminData(dataSource.getAllAttendance())
    }

    private fun append(message: ChatMessage) {
        _messages.value = (_messages.value ?: emptyList()) + message
    }

    private fun welcomeText(role: Role, userName: String): String {
        val greeting = if (userName.isBlank()) "Xin chào!" else "Xin chào $userName!"
        val scope = when (role) {
            Role.STUDENT -> "Bạn có thể hỏi mình về số buổi vắng, tỷ lệ chuyên cần hay nguy cơ cấm thi của bạn."
            Role.TEACHER -> "Thầy/cô có thể hỏi mình về tình hình chuyên cần của các lớp đang dạy."
            Role.ADMIN -> "Bạn có thể hỏi mình về tình hình chuyên cần của toàn hệ thống."
        }
        return "$greeting Mình là trợ lý AI của Kstudy. $scope"
    }

    companion object {
        const val MAX_HISTORY_TURNS = 8
        const val EMPTY_ANSWER = "AI chưa trả lời được câu hỏi này. Bạn thử hỏi lại theo cách khác nhé."
        const val AI_FAILED = "Không kết nối được với trợ lý AI. Vui lòng thử lại sau."
        const val DATA_FAILED = "Không tải được dữ liệu điểm danh. Vui lòng kiểm tra kết nối mạng và thử lại."

        fun suggestionsFor(role: Role): List<String> = when (role) {
            Role.STUDENT -> listOf(
                "Em đã vắng bao nhiêu buổi?",
                "Em có nguy cơ bị cấm thi không?",
                "Lớp nào em đi học đầy đủ nhất?",
                "Lịch học của em thế nào?"
            )
            Role.TEACHER -> listOf(
                "Học sinh nào vắng nhiều nhất?",
                "Ai có nguy cơ bị cấm thi?",
                "Tóm tắt chuyên cần các lớp của tôi",
                "Buổi nào vắng nhiều nhất?"
            )
            Role.ADMIN -> listOf(
                "Lớp nào có tỷ lệ đi học thấp nhất?",
                "Có bao nhiêu học sinh nguy cơ cấm thi?",
                "Tóm tắt chuyên cần toàn hệ thống"
            )
        }
    }
}
