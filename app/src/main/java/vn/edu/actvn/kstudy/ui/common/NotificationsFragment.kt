package vn.edu.actvn.kstudy.ui.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.notification.NotificationCenter
import vn.edu.actvn.kstudy.ui.adapter.NotificationAdapter

// Everything the signed-in user has been notified of, newest first
class NotificationsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        val btnClear = view.findViewById<View>(R.id.btnClearNotifications)
        val rv = view.findViewById<RecyclerView>(R.id.rvNotifications)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = NotificationAdapter(emptyList())
        rv.adapter = adapter
        val listState = ListState(view)

        view.findViewById<View>(R.id.btnBackNotifications).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        btnClear.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa tất cả thông báo")
                .setMessage("Các thông báo đã xóa không xem lại được.")
                .setPositiveButton("Xóa") { _, _ -> NotificationCenter.clearAll() }
                .setNegativeButton("Hủy", null)
                .show()
        }

        NotificationCenter.notifications.observe(viewLifecycleOwner) { notifications ->
            adapter.updateList(notifications)
            listState.show(notifications.size, "Chưa có thông báo nào.")
            btnClear.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
        }

        return view
    }

    override fun onDestroyView() {
        // Read only once the user leaves, so the dots of what was new stay while they look
        NotificationCenter.markAllRead()
        super.onDestroyView()
    }
}

// The bell in the header of a dashboard (view_notification_bell.xml)
object NotificationBell {

    // Shows how many notifications are unread and opens the list in the given container
    fun bind(fragment: Fragment, root: View, containerId: Int) {
        val bell = root.findViewById<View>(R.id.btnNotifications)
        val badge = root.findViewById<TextView>(R.id.tvNotificationBadge)

        NotificationCenter.notifications.observe(fragment.viewLifecycleOwner) { notifications ->
            val unread = notifications.count { !it.read }
            badge.text = if (unread > MAX_COUNT) "$MAX_COUNT+" else unread.toString()
            badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
            bell.contentDescription = if (unread > 0) "Thông báo, $unread chưa đọc" else "Thông báo"
        }
        bell.setOnClickListener {
            fragment.parentFragmentManager.beginTransaction()
                .replace(containerId, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    // A larger count is written as "9+", which still fits the badge
    private const val MAX_COUNT = 9
}
