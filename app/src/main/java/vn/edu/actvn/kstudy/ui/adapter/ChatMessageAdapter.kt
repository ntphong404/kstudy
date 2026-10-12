package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.ChatMessage

class ChatMessageAdapter(
    private var list: List<ChatMessage>
) : RecyclerView.Adapter<ChatMessageAdapter.ViewHolder>() {

    fun updateList(newList: List<ChatMessage>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (list[position].fromUser) TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tvChatText)

        fun bind(message: ChatMessage) {
            tvText.text = message.text
            if (message.fromUser) return

            // AI bubbles are recycled between normal answers and error notices
            val context = itemView.context
            if (message.isError) {
                tvText.setBackgroundResource(R.drawable.bg_chat_error)
                tvText.setTextColor(ContextCompat.getColor(context, R.color.on_error_container))
            } else {
                tvText.setBackgroundResource(R.drawable.bg_chat_ai)
                tvText.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            }
        }
    }

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
    }
}
