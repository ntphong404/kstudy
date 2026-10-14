package vn.edu.actvn.kstudy.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException

// Calls Gemini through Firebase AI Logic (Gemini Developer API), so the app needs no API key
// and no server of its own. The feature has to be enabled once in the Firebase console.
class GeminiAiService(private val modelName: String = DEFAULT_MODEL) : AiService {

    override suspend fun generate(systemInstruction: String, history: List<AiTurn>, message: String): String {
        try {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = modelName,
                systemInstruction = content { text(systemInstruction) }
            )
            val chat = model.startChat(
                history = history.flatMap { turn ->
                    listOf(
                        content(role = "user") { text(turn.question) },
                        content(role = "model") { text(turn.answer) }
                    )
                }
            )
            return chat.sendMessage(message).text?.trim().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Kstudy", "Gemini request failed: ${e.message}", e)
            throw AiServiceException(userMessageFor(e), e)
        }
    }

    private fun userMessageFor(e: Exception): String {
        val detail = generateSequence<Throwable>(e) { it.cause }
            .joinToString(" ") { "${it.javaClass.simpleName} ${it.message.orEmpty()}" }
            .lowercase()
        return when {
            "unknownhost" in detail || "unable to resolve host" in detail || "connectexception" in detail ->
                "Không có kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            "timeout" in detail ->
                "Trợ lý AI phản hồi quá lâu. Vui lòng thử lại."
            "quota" in detail || "resource_exhausted" in detail || "429" in detail ->
                "Trợ lý AI đang quá tải (hết lượt gọi miễn phí trong phút này). Vui lòng thử lại sau ít phút."
            "must enforce firebase app check" in detail || "app check enforcement" in detail ->
                "Firebase AI Logic yêu cầu bật App Check ở mục APIs trong Firebase Console."
            "app check" in detail || "appcheck" in detail ->
                "App Check từ bản cài này chưa được chấp nhận. Hãy kiểm tra cấu hình App Check và dự án Firebase."
            "apinotconfigured" in detail || "servicedisabled" in detail || "has not been used" in detail ||
                "permission_denied" in detail || "403" in detail ->
                "Tính năng AI chưa được bật cho dự án Firebase. Hãy bật Firebase AI Logic trong Firebase Console."
            "blocked" in detail ->
                "Câu hỏi bị bộ lọc an toàn của AI từ chối. Bạn thử diễn đạt lại nhé."
            else ->
                "Không kết nối được với trợ lý AI. Vui lòng thử lại sau."
        }
    }

    companion object {
        // A stable model that does not need the paid Blaze plan; change it here if Google retires it
        const val DEFAULT_MODEL = "gemini-3.5-flash"
    }
}
