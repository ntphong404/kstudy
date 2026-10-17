package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.util.ScheduleUtils

class ClassAdapter(
    private var list: List<CourseClass>,
    private val onEditClick: (CourseClass) -> Unit,
    private val onDeleteClick: (CourseClass) -> Unit,
    private val onRosterClick: (CourseClass) -> Unit // Manage enrolled students
) : RecyclerView.Adapter<ClassAdapter.ViewHolder>() {

    fun updateList(newList: List<CourseClass>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val courseClass = list[position]
        holder.bind(courseClass, onEditClick, onDeleteClick, onRosterClick)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvClassName)
        private val tvSubject: TextView = view.findViewById(R.id.tvClassSubject)
        private val tvRoom: TextView = view.findViewById(R.id.tvClassRoom)
        private val tvSchedule: TextView = view.findViewById(R.id.tvClassSchedule)
        private val tvStudentCount: TextView = view.findViewById(R.id.tvStudentCount)
        private val btnEdit: Button = view.findViewById(R.id.btnEditClass)
        private val btnDelete: Button = view.findViewById(R.id.btnDeleteClass)
        private val btnRoster: Button = view.findViewById(R.id.btnManageRoster)

        fun bind(
            courseClass: CourseClass,
            onEdit: (CourseClass) -> Unit,
            onDelete: (CourseClass) -> Unit,
            onRoster: (CourseClass) -> Unit
        ) {
            tvName.text = courseClass.name
            tvSubject.text = "Năm học: ${courseClass.academicYear}"
            tvRoom.text = "Phòng: ${courseClass.room}"
            val dates = ScheduleUtils.datesLabel(courseClass)
            tvSchedule.text = "Lịch học: ${courseClass.schedule}" + if (dates.isEmpty()) "" else "\nThời gian: $dates"
            tvStudentCount.text = "${courseClass.totalStudents} học sinh"

            btnEdit.setOnClickListener { onEdit(courseClass) }
            btnDelete.setOnClickListener { onDelete(courseClass) }
            btnRoster.setOnClickListener { onRoster(courseClass) }
        }
    }
}
