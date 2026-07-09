package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.CourseClass

class ClassSelectionAdapter(
    private var list: List<CourseClass>,
    private var attendanceRates: Map<String, Int>? = null, // key is classId, value is rate (0-100)
    private val onItemClick: (CourseClass) -> Unit
) : RecyclerView.Adapter<ClassSelectionAdapter.ViewHolder>() {

    fun updateData(newList: List<CourseClass>, newRates: Map<String, Int>? = null) {
        list = newList
        attendanceRates = newRates
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val courseClass = list[position]
        val rate = attendanceRates?.get(courseClass.id)
        holder.bind(courseClass, rate, onItemClick)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvClassName: TextView = view.findViewById(R.id.tvSelClassName)
        private val tvSubject: TextView = view.findViewById(R.id.tvSelSubject)
        private val tvRoom: TextView = view.findViewById(R.id.tvSelRoom)
        private val tvSchedule: TextView = view.findViewById(R.id.tvSelSchedule)
        
        // Student specific
        private val rateContainer: View = view.findViewById(R.id.studentRateContainer)
        private val tvRateText: TextView = view.findViewById(R.id.tvSelRate)
        private val rateProgressBar: ProgressBar = view.findViewById(R.id.pbSelRate)
        
        // Teacher specific
        private val tvStudentCount: TextView = view.findViewById(R.id.tvSelStudentCount)

        fun bind(courseClass: CourseClass, rate: Int?, onClick: (CourseClass) -> Unit) {
            tvClassName.text = courseClass.name
            tvSubject.text = "Năm học: ${courseClass.academicYear}"
            tvRoom.text = "Phòng: ${courseClass.room}"
            tvSchedule.text = courseClass.schedule

            if (rate != null) {
                // Student mode
                rateContainer.visibility = View.VISIBLE
                tvStudentCount.visibility = View.GONE
                tvRateText.text = "Chuyên cần: $rate%"
                rateProgressBar.progress = rate
            } else {
                // Teacher mode
                rateContainer.visibility = View.GONE
                tvStudentCount.visibility = View.VISIBLE
                tvStudentCount.text = "Sĩ số: ${courseClass.totalStudents} học sinh"
            }

            itemView.setOnClickListener { onClick(courseClass) }
        }
    }
}
