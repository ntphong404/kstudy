package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Teacher

class TeacherAdapter(
    private var list: List<Teacher>,
    private val onEditClick: (Teacher) -> Unit,
    private val onDeleteClick: (Teacher) -> Unit
) : RecyclerView.Adapter<TeacherAdapter.ViewHolder>() {

    fun updateList(newList: List<Teacher>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_teacher, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val teacher = list[position]
        holder.bind(teacher, onEditClick, onDeleteClick)
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvTeacherName)
        private val tvSubject: TextView = view.findViewById(R.id.tvTeacherSubject)
        private val tvPhone: TextView = view.findViewById(R.id.tvTeacherPhone)
        private val btnEdit: Button = view.findViewById(R.id.btnEditTeacher)
        private val btnDelete: Button = view.findViewById(R.id.btnDeleteTeacher)
        private val tvInitials: TextView = view.findViewById(R.id.tvInitials)

        fun bind(teacher: Teacher, onEdit: (Teacher) -> Unit, onDelete: (Teacher) -> Unit) {
            tvName.text = teacher.name
            tvSubject.text = teacher.subject
            tvPhone.text = "SĐT: ${teacher.phone.ifBlank { "N/A" }}"
            
            // initials for avatar
            val initials = if (teacher.name.isNotBlank()) {
                val parts = teacher.name.split(" ")
                if (parts.size >= 2) {
                    "${parts[parts.size - 2].take(1)}${parts.last().take(1)}"
                } else {
                    teacher.name.take(2)
                }
            } else {
                "GV"
            }
            tvInitials.text = initials.uppercase()

            btnEdit.setOnClickListener { onEdit(teacher) }
            btnDelete.setOnClickListener { onDelete(teacher) }
        }
    }
}
