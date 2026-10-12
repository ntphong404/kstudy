package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AttendanceRecord
import vn.edu.actvn.kstudy.ui.common.AttendanceViews

class AttendanceHistoryAdapter(
    private var list: List<AttendanceRecord>
) : RecyclerView.Adapter<AttendanceHistoryAdapter.ViewHolder>() {

    fun updateList(newList: List<AttendanceRecord>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = list[position]
        holder.bind(record)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvHistoryDate)
        private val tvStatus: TextView = view.findViewById(R.id.tvHistoryStatus)
        private val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)

        fun bind(record: AttendanceRecord) {
            // record.timeMarked holds something like "08:15 (22/05/2024)"
            val fullTime = record.timeMarked
            if (fullTime.contains("(") && fullTime.contains(")")) {
                val timePart = fullTime.substringBefore(" (").trim()
                val datePart = fullTime.substringAfter("(").substringBefore(")").trim()
                tvDate.text = "Ngày: $datePart"
                tvTime.text = if (timePart == "-") "Vắng cả buổi" else "Thời gian quét: $timePart"
            } else {
                tvDate.text = "Chi tiết buổi học"
                tvTime.text = "Thời gian: $fullTime"
            }

            tvStatus.text = when (record.status) {
                "PRESENT" -> "Có mặt"
                "LATE" -> "Đi muộn"
                "ABSENT" -> "Vắng mặt"
                "EXCUSED" -> "Vắng có phép"
                else -> record.status
            }
            if (record.status == "EXCUSED") {
                tvTime.text = "Đơn xin nghỉ đã được duyệt"
            }
            val (textColor, tagColor) = AttendanceViews.statusColors(record.status)
            AttendanceViews.tintTag(tvStatus, textColor, tagColor)
        }
    }
}
