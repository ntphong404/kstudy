package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AppNotification
import vn.edu.actvn.kstudy.util.NotificationRules

class NotificationAdapter(
    private var list: List<AppNotification>
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    fun updateList(newList: List<AppNotification>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val unread: View = view.findViewById(R.id.viewNotificationUnread)
        private val tvTitle: TextView = view.findViewById(R.id.tvNotificationTitle)
        private val tvTime: TextView = view.findViewById(R.id.tvNotificationTime)
        private val tvBody: TextView = view.findViewById(R.id.tvNotificationBody)

        fun bind(notification: AppNotification) {
            unread.visibility = if (notification.read) View.GONE else View.VISIBLE
            tvTitle.text = notification.title
            tvTime.text = NotificationRules.timeLabel(notification.createdAt, System.currentTimeMillis())
            tvBody.text = notification.body
        }
    }
}
