package vn.edu.actvn.kstudy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.common.AttendanceViews
import vn.edu.actvn.kstudy.util.ImportRow
import vn.edu.actvn.kstudy.util.ImportRowStatus

// The rows of an import file, each with what will happen to it
class ImportRowAdapter(
    private var list: List<ImportRow>
) : RecyclerView.Adapter<ImportRowAdapter.ViewHolder>() {

    fun updateList(newList: List<ImportRow>) {
        list = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_import_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvImportRowName)
        private val tvStatus: TextView = view.findViewById(R.id.tvImportRowStatus)
        private val tvDetails: TextView = view.findViewById(R.id.tvImportRowDetails)
        private val tvNote: TextView = view.findViewById(R.id.tvImportRowNote)

        fun bind(row: ImportRow) {
            val student = row.entry.student
            tvName.text = student.name.ifEmpty { "(chưa có họ tên)" }
            tvDetails.text = listOf(
                "Dòng ${row.line}",
                student.studentCode,
                student.className,
                // The account only matters for a student who is about to get one
                if (row.status == ImportRowStatus.NEW) "Tài khoản: ${row.entry.username}" else ""
            ).filter { it.isNotEmpty() }.joinToString(" • ")

            when (row.status) {
                ImportRowStatus.NEW -> {
                    tvStatus.text = "Hợp lệ"
                    AttendanceViews.tintTag(tvStatus, R.color.present, R.color.present_bg)
                }
                ImportRowStatus.EXISTING -> {
                    tvStatus.text = "Đã có"
                    AttendanceViews.tintTag(tvStatus, R.color.on_primary_container, R.color.primary_container)
                }
                ImportRowStatus.INVALID -> {
                    tvStatus.text = "Lỗi"
                    AttendanceViews.tintTag(tvStatus, R.color.absent, R.color.absent_bg)
                }
            }

            tvNote.text = row.note
            tvNote.visibility = if (row.note.isEmpty()) View.GONE else View.VISIBLE
            tvNote.setTextColor(
                ContextCompat.getColor(
                    tvNote.context,
                    if (row.status == ImportRowStatus.INVALID) R.color.error else R.color.text_secondary
                )
            )
        }
    }
}
