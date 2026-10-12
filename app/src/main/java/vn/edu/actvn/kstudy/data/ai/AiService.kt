package vn.edu.actvn.kstudy.data.ai

// One finished exchange of a conversation, replayed to the model so it remembers the context
data class AiTurn(
    val question: String,
    val answer: String
)

// The message is written for the end user and can be shown as is
class AiServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface AiService {
    // Throws AiServiceException when no answer could be produced
    suspend fun generate(systemInstruction: String, history: List<AiTurn>, message: String): String
}
