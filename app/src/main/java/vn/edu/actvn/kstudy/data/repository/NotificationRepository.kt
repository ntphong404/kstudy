package vn.edu.actvn.kstudy.data.repository

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import vn.edu.actvn.kstudy.data.model.AppNotification

// Where notifications are prepared and sent. Not the scope of a ViewModel: the screen that
// causes a notification often closes itself a moment later (the QR code screen does, right after
// closing a session), and cancelling its ViewModel would stop the notification on its way.
val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

// Notifications live in the `notifications` collection, one document for each recipient. The
// phone of the sender writes them; the phone of the recipient listens for them.
class NotificationRepository {
    private val firestore = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T = withContext(Dispatchers.IO) {
        Tasks.await(task)
    }

    // A notification is a courtesy on top of what the user just did (opening a session, deciding
    // a leave request): when it cannot be sent, that action must not fail with it.
    suspend fun sendQuietly(notifications: List<AppNotification>, withdrawIds: List<String> = emptyList()) {
        try {
            val collection = firestore.collection(COLLECTION)
            for (chunk in notifications.chunked(BATCH_LIMIT)) {
                val batch = firestore.batch()
                for (notification in chunk) {
                    // With an id of its own a notification replaces the one sent before under that id
                    val document = if (notification.id.isEmpty()) collection.document() else collection.document(notification.id)
                    batch.set(document, notification.toFields())
                }
                await(batch.commit())
            }
            for (chunk in withdrawIds.chunked(BATCH_LIMIT)) {
                val batch = firestore.batch()
                chunk.forEach { batch.delete(collection.document(it)) }
                await(batch.commit())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not send ${notifications.size} notification(s)", e)
        }
    }

    // Calls back on the main thread with every notification of the user: once straight away
    // and again whenever one is added, read or removed. The result is not sorted: ordering in
    // the query would need a composite index to be created in the Firebase console first.
    fun listen(userId: String, onChange: (List<AppNotification>) -> Unit): ListenerRegistration =
        firestore.collection(COLLECTION)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.w(TAG, "Listening for notifications failed", error)
                    return@addSnapshotListener
                }
                onChange(snapshot.documents.map { it.toNotification() })
            }

    // These two are not awaited: the listener shows the result as soon as it is written
    fun markRead(ids: List<String>) {
        for (chunk in ids.chunked(BATCH_LIMIT)) {
            val batch = firestore.batch()
            chunk.forEach { batch.update(firestore.collection(COLLECTION).document(it), "read", true) }
            batch.commit().addOnFailureListener { Log.w(TAG, "Could not mark notifications as read", it) }
        }
    }

    fun delete(ids: List<String>) {
        for (chunk in ids.chunked(BATCH_LIMIT)) {
            val batch = firestore.batch()
            chunk.forEach { batch.delete(firestore.collection(COLLECTION).document(it)) }
            batch.commit().addOnFailureListener { Log.w(TAG, "Could not delete notifications", it) }
        }
    }

    private fun AppNotification.toFields(): Map<String, Any> = mapOf(
        "userId" to userId,
        "type" to type,
        "title" to title,
        "body" to body,
        "createdAt" to createdAt,
        "read" to read
    )

    private fun DocumentSnapshot.toNotification() = AppNotification(
        id = id,
        userId = getString("userId") ?: "",
        type = getString("type") ?: "",
        title = getString("title") ?: "",
        body = getString("body") ?: "",
        createdAt = getLong("createdAt") ?: 0L,
        read = getBoolean("read") ?: false
    )

    private companion object {
        const val COLLECTION = "notifications"
        const val TAG = "Kstudy"

        // A Firestore batch takes at most 500 writes
        const val BATCH_LIMIT = 400
    }
}
