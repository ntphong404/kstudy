package vn.edu.actvn.kstudy.data.notification

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.ListenerRegistration
import vn.edu.actvn.kstudy.data.model.AppNotification
import vn.edu.actvn.kstudy.data.repository.NotificationRepository
import vn.edu.actvn.kstudy.util.NotificationRules

// The notifications of the signed-in user, kept up to date for as long as the app is running.
// It belongs to the app and not to a screen: a new notification has to reach the status bar
// whichever screen is open, and while the app is in the background.
object NotificationCenter {

    // More than this many new notifications at once are announced as one line instead of a flood
    private const val MAX_SEPARATE_ANNOUNCEMENTS = 3
    private const val PREFERENCES = "notifications"
    private const val SUMMARY_ID = 1

    private val repository by lazy { NotificationRepository() }
    private var registration: ListenerRegistration? = null
    private var userId = ""

    private val _notifications = MutableLiveData<List<AppNotification>>(emptyList())

    // Newest first
    val notifications: LiveData<List<AppNotification>> = _notifications

    // Called when a student or a teacher has signed in
    fun start(context: Context, userId: String) {
        if (userId.isEmpty() || (userId == this.userId && registration != null)) return
        stop()
        this.userId = userId
        val appContext = context.applicationContext
        registration = repository.listen(userId) { received ->
            val sorted = NotificationRules.newestFirst(received)
            _notifications.value = sorted
            announce(appContext, userId, sorted)
        }
    }

    // Signing out on a shared phone: the next user must neither receive this account's
    // notifications nor find them in the status bar, and its lesson reminders must not go off
    fun signOut(context: Context) {
        stop()
        SystemNotifier.cancelAll(context)
        ClassReminders.cancelAll(context)
    }

    // Stops listening, e.g. when the sign-in screen shows: nobody is signed in there
    fun stop() {
        registration?.remove()
        registration = null
        userId = ""
        _notifications.value = emptyList()
    }

    fun markAllRead() {
        val unread = _notifications.value.orEmpty().filter { !it.read }.map { it.id }
        if (unread.isNotEmpty()) repository.markRead(unread)
    }

    fun clearAll() {
        val ids = _notifications.value.orEmpty().map { it.id }
        if (ids.isNotEmpty()) repository.delete(ids)
    }

    // Called once the user allows notifications: what arrived while they were not allowed (at the
    // first sign-in the listener starts before the question is answered) can be shown now
    fun onNotificationsAllowed(context: Context) {
        val signedIn = userId
        if (signedIn.isNotEmpty()) announce(context.applicationContext, signedIn, _notifications.value.orEmpty())
    }

    // Brings the status bar in line with the notifications. Which ones this phone has dealt with
    // is remembered across restarts, so signing in again does not repeat them; what arrived while
    // the app was closed is announced at the next sign-in.
    private fun announce(context: Context, userId: String, notifications: List<AppNotification>) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val announced = preferences.getStringSet(userId, emptySet()).orEmpty()
        val plan = NotificationRules.planAnnouncements(notifications, announced, SystemNotifier.canNotify(context))

        // A withdrawn absence notice or a notification read in the app leaves the status bar too
        for (id in plan.withdraw) SystemNotifier.cancel(context, id.hashCode())
        if (notifications.none { !it.read }) SystemNotifier.cancel(context, SUMMARY_ID)

        val fresh = plan.post
        if (fresh.size > MAX_SEPARATE_ANNOUNCEMENTS) {
            SystemNotifier.show(
                context, SUMMARY_ID,
                title = "Bạn có ${fresh.size} thông báo mới",
                body = fresh.last().title,
                channel = SystemNotifier.CHANNEL_CLASS_ACTIVITY
            )
        } else {
            for (notification in fresh) {
                SystemNotifier.show(
                    context, notification.id.hashCode(),
                    notification.title, notification.body, SystemNotifier.CHANNEL_CLASS_ACTIVITY
                )
            }
        }
        // Only notifications that still exist are remembered, so the set cannot grow for ever
        if (plan.remember != announced) preferences.edit().putStringSet(userId, plan.remember).apply()
    }
}
