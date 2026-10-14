package vn.edu.actvn.kstudy.data.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.util.ClassReminder
import vn.edu.actvn.kstudy.util.NotificationRules
import java.util.Calendar

// "Your lesson starts in 15 minutes": alarms set on the phone itself from the schedule of the
// user's classes, so they go off without the app running and without a network.
object ClassReminders {

    // Far more than the lessons anybody has in a week; also the range of ids to cancel
    private const val MAX_REMINDERS = 40

    private const val EXTRA_ID = "id"
    private const val EXTRA_AT = "at"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"

    // Replaces the alarms set before with one for the next occurrence of every lesson. Each alarm
    // sets itself again for the following week when it goes off (see onAlarm), so the reminders
    // keep coming without the app being opened; the dashboards call this whenever they load the
    // classes, which brings the alarms in line with a schedule that has changed.
    fun schedule(context: Context, classes: List<CourseClass>, now: Calendar = Calendar.getInstance()) {
        val appContext = context.applicationContext
        cancelAll(appContext)
        NotificationRules.classReminders(classes, now).take(MAX_REMINDERS).forEachIndexed { id, reminder ->
            setAlarm(appContext, id, reminder)
        }
    }

    // A reminder has gone off: show it, and set the same alarm for the same lesson a week later
    internal fun onAlarm(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val id = intent.getIntExtra(EXTRA_ID, 0)
        SystemNotifier.show(context, NOTIFICATION_ID_BASE + id, title, body, SystemNotifier.CHANNEL_REMINDERS)

        val at = intent.getLongExtra(EXTRA_AT, 0L)
        if (at > 0L) {
            val next = NotificationRules.nextWeek(at, System.currentTimeMillis())
            setAlarm(context.applicationContext, id, ClassReminder(next, title, body))
        }
    }

    private fun setAlarm(context: Context, id: Int, reminder: ClassReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_AT, reminder.atMillis)
            .putExtra(EXTRA_TITLE, reminder.title)
            .putExtra(EXTRA_BODY, reminder.body)
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // An inexact alarm set days ahead may go off up to an hour late, after the lesson has
        // begun. An exact one is used where Android allows it (USE_EXACT_ALARM is granted on
        // install); otherwise a window of a few minutes around the time.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pending)
                return
            }
        } catch (e: SecurityException) {
            // The permission was taken away in the system settings
        }
        alarms.setWindow(
            AlarmManager.RTC_WAKEUP, reminder.atMillis - FALLBACK_WINDOW_MILLIS / 2, FALLBACK_WINDOW_MILLIS, pending
        )
    }

    // The shortest window Android grants without the exact alarm permission
    private const val FALLBACK_WINDOW_MILLIS = 10 * 60_000L

    // Keeps the reminders apart from the ids of the other notifications
    private const val NOTIFICATION_ID_BASE = 1000

    // Called on sign-out, and before setting the alarms again
    fun cancelAll(context: Context) {
        val appContext = context.applicationContext
        val alarms = appContext.getSystemService(AlarmManager::class.java) ?: return
        for (id in 0 until MAX_REMINDERS) {
            val pending = PendingIntent.getBroadcast(
                appContext, id, Intent(appContext, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            alarms.cancel(pending)
            pending.cancel()
        }
    }
}

// Woken by an alarm of ClassReminders, whether or not the app is running
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ClassReminders.onAlarm(context, intent)
    }
}
