package vn.edu.actvn.kstudy.data.model

// One bubble on the AI assistant screen
data class ChatMessage(
    val text: String = "",
    val fromUser: Boolean = false,
    val isError: Boolean = false
)
