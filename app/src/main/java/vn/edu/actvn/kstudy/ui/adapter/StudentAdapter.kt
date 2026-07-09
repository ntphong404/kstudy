package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Student

class StudentAdapter(
    private var list: List<Student>,
    private val onEditClick: ((Student) -> Unit)? = null,
    private val onDeleteClick: ((Student) -> Unit)? = null,
    private val onActionClick: ((Student) -> Unit)? = null,
    private val actionText: String? = null
) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    fun updateList(newList: List<Student>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_student, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = list[position]
        holder.bind(student, onEditClick, onDeleteClick, onActionClick, actionText)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvStudentName)
        private val tvCode: TextView = view.findViewById(R.id.tvStudentCode)
        private val tvClass: TextView = view.findViewById(R.id.tvStudentClass)
        private val tvPhone: TextView = view.findViewById(R.id.tvStudentPhone)
        private val btnEdit: Button = view.findViewById(R.id.btnEditStudent)
        private val btnDelete: Button = view.findViewById(R.id.btnDeleteStudent)
        private val btnAction: Button = view.findViewById(R.id.btnActionStudent)
        private val tvInitials: TextView = view.findViewById(R.id.tvStudentInitials)

        fun bind(
            student: Student,
            onEdit: ((Student) -> Unit)?,
            onDelete: ((Student) -> Unit)?,
            onAction: ((Student) -> Unit)?,
            actionLabel: String?
        ) {
            tvName.text = student.name
            tvCode.text = "Mã: ${student.studentCode}"
            tvClass.text = "Lớp: ${student.className}"
            tvPhone.text = "SĐT: ${student.phone.ifBlank { "N/A" }}"

            // initials
            val initials = if (student.name.isNotBlank()) {
                val parts = student.name.split(" ")
                if (parts.size >= 2) {
                    "${parts[parts.size - 2].take(1)}${parts.last().take(1)}"
                } else {
                    student.name.take(2)
                }
            } else {
                "HS"
            }
            tvInitials.text = initials.uppercase()

            // Admin edit/delete buttons
            if (onEdit != null && onDelete != null) {
                btnEdit.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE
                btnEdit.setOnClickListener { onEdit(student) }
                btnDelete.setOnClickListener { onDelete(student) }
            } else {
                btnEdit.visibility = View.GONE
                btnDelete.visibility = View.GONE
            }

            // Custom context action (e.g. Enroll/Unenroll)
            if (onAction != null && actionLabel != null) {
                btnAction.visibility = View.VISIBLE
                btnAction.text = actionLabel
                btnAction.setOnClickListener { onAction(student) }
                
                val context = btnAction.context
                if (actionLabel.contains("Xóa", ignoreCase = true)) {
                    btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.absent))
                } else {
                    btnAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
                }
            } else {
                btnAction.visibility = View.GONE
            }
        }
    }
}
