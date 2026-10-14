package vn.edu.actvn.kstudy.data.model

// A message for one user (`notifications` collection); it stays there until the user clears it
data class AppNotification(
    val id: String = "", // empty for a new one that gets a generated id
    val userId: String = "", // who it is for
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val createdAt: Long = 0L,
    val read: Boolean = false
)

// Values of the `type` field in the `notifications` collection
object NotificationType {
    const val SESSION_OPENED = "SESSION_OPENED"
    const val ABSENCE = "ABSENCE"
    const val LEAVE_SUBMITTED = "LEAVE_SUBMITTED"
    const val LEAVE_DECIDED = "LEAVE_DECIDED"
}
