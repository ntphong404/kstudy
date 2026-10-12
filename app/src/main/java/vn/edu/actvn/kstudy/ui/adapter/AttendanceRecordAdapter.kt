package vn.edu.actvn.kstudy.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.ui.common.AttendanceViews

class AttendanceRecordAdapter(
    private var list: List<AttendanceRecord>,
    private val onStatusChanged: (AttendanceRecord, String) -> Unit // returns record and new status
) : RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder>() {

    fun updateList(newList: List<AttendanceRecord>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = list[position]
        holder.bind(record, onStatusChanged)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvRecordStudentName)
        private val tvCode: TextView = view.findViewById(R.id.tvRecordStudentCode)
        private val tvTime: TextView = view.findViewById(R.id.tvRecordTime)

        // Each button with the status it sets
        private val statusButtons: List<Pair<Button, String>> = listOf(
            view.findViewById<Button>(R.id.btnStatusPresent) to "PRESENT",
            view.findViewById<Button>(R.id.btnStatusLate) to "LATE",
            view.findViewById<Button>(R.id.btnStatusAbsent) to "ABSENT",
            view.findViewById<Button>(R.id.btnStatusExcused) to "EXCUSED"
        )

        fun bind(record: AttendanceRecord, onStatusChange: (AttendanceRecord, String) -> Unit) {
            tvName.text = record.studentName
            tvCode.text = record.studentCode
            // No time yet: the line is left out so the name and code stay centred next to the buttons
            val hasTime = record.timeMarked != "-"
            tvTime.text = if (hasTime) "Lúc: ${record.timeMarked}" else ""
            tvTime.visibility = if (hasTime) View.VISIBLE else View.GONE

            for ((button, status) in statusButtons) {
                highlight(button, status, selected = record.status == status)
                button.setOnClickListener {
                    if (record.status != status) onStatusChange(record, status)
                }
            }
        }

        // The current status is filled with its own colour; the other three stay neutral
        private fun highlight(button: Button, status: String, selected: Boolean) {
            val context = button.context
            val background = if (selected) AttendanceViews.statusColors(status).first else R.color.surface_container
            val text = if (selected) R.color.on_primary else R.color.text_secondary
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
            button.setTextColor(ContextCompat.getColor(context, text))
        }
    }
}
