package vn.edu.actvn.kstudy.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R

// Android 13 and later show notifications only after the user has allowed them
object NotificationPermission {

    // Asks once the user is signed in. After two refusals Android stops showing the question
    // and this does nothing.
    fun requestIfNeeded(context: Context, launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// Puts a notification in the status bar of the phone
object SystemNotifier {

    // What happens in a class: a session is opened, an absence is recorded, a leave request moves on
    const val CHANNEL_CLASS_ACTIVITY = "class_activity"

    // "Your lesson starts in 15 minutes"
    const val CHANNEL_REMINDERS = "class_reminders"

    // id: showing a notification again under the same id replaces it instead of stacking up
    fun show(context: Context, id: Int, title: String, body: String, channel: String) {
        if (!canNotify(context)) return
        ensureChannels(context)

        // Same as tapping the app icon: the app comes back the way it was left
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was taken away between the check and the call
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    // Removes every notification of the app from the status bar
    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    // Android 13 and later only show notifications once the user has allowed them
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Creating a channel that already exists changes nothing
    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CLASS_ACTIVITY, "Hoạt động lớp học", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Mở phiên điểm danh, kết quả điểm danh, đơn xin nghỉ"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Nhắc lịch học", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nhắc trước giờ lên lớp"
            }
        )
    }
}
