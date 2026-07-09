package vn.edu.actvn.kstudy.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.AttendanceRecord

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
        private val btnPresent: Button = view.findViewById(R.id.btnStatusPresent)
        private val btnLate: Button = view.findViewById(R.id.btnStatusLate)
        private val btnAbsent: Button = view.findViewById(R.id.btnStatusAbsent)

        fun bind(record: AttendanceRecord, onStatusChange: (AttendanceRecord, String) -> Unit) {
            tvName.text = record.studentName
            tvCode.text = record.studentCode
            tvTime.text = if (record.timeMarked == "-") "" else "Lúc: ${record.timeMarked}"

            // Reset buttons styling to defaults
            resetButtonStyles()

            // Highlight current status
            when (record.status) {
                "PRESENT" -> {
                    btnPresent.setBackgroundColor(Color.parseColor("#4CAF50"))
                    btnPresent.setTextColor(Color.WHITE)
                }
                "LATE" -> {
                    btnLate.setBackgroundColor(Color.parseColor("#FFC107"))
                    btnLate.setTextColor(Color.WHITE)
                }
                "ABSENT" -> {
                    btnAbsent.setBackgroundColor(Color.parseColor("#F44336"))
                    btnAbsent.setTextColor(Color.WHITE)
                }
            }

            btnPresent.setOnClickListener {
                if (record.status != "PRESENT") onStatusChange(record, "PRESENT")
            }
            btnLate.setOnClickListener {
                if (record.status != "LATE") onStatusChange(record, "LATE")
            }
            btnAbsent.setOnClickListener {
                if (record.status != "ABSENT") onStatusChange(record, "ABSENT")
            }
        }

        private fun resetButtonStyles() {
            val context = itemView.context
            
            // Set unselected flat styles
            btnPresent.setBackgroundColor(Color.parseColor("#EDEFEF"))
            btnPresent.setTextColor(Color.parseColor("#5A5C5D"))
            
            btnLate.setBackgroundColor(Color.parseColor("#EDEFEF"))
            btnLate.setTextColor(Color.parseColor("#5A5C5D"))
            
            btnAbsent.setBackgroundColor(Color.parseColor("#EDEFEF"))
            btnAbsent.setTextColor(Color.parseColor("#5A5C5D"))
        }
    }
}
