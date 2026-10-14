package vn.edu.actvn.kstudy.ui.common

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.util.Lesson
import vn.edu.actvn.kstudy.util.TimetableDay
import vn.edu.actvn.kstudy.util.WeekTimetable
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import vn.edu.actvn.kstudy.viewmodel.TimetableState
import vn.edu.actvn.kstudy.viewmodel.TimetableViewModel

// The week of a student or a teacher as a grid: one row per day, one column per time slot
class TimetableFragment : Fragment() {

    private var userId: String = ""
    private var role: Role = Role.STUDENT

    private lateinit var viewModel: TimetableViewModel

    companion object {
        fun newInstance(userId: String, role: Role): TimetableFragment {
            return TimetableFragment().apply {
                arguments = Bundle().apply {
                    putString("userId", userId)
                    putString("role", role.name)
                }
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        arguments?.let {
            userId = it.getString("userId") ?: ""
            role = if (it.getString("role") == Role.TEACHER.name) Role.TEACHER else Role.STUDENT
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_timetable, container, false)

        viewModel = ViewModelProvider(this, KstudyViewModelFactory)[TimetableViewModel::class.java]

        val tvWeek = view.findViewById<TextView>(R.id.tvTimetableWeek)
        val grid = view.findViewById<LinearLayout>(R.id.layoutTimetableGrid)
        val tvNote = view.findViewById<View>(R.id.tvTimetableNote)
        val listState = ListState(view)

        view.findViewById<View>(R.id.btnBackTimetable).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TimetableState.Loading -> Unit
                is TimetableState.Error -> {
                    grid.visibility = View.GONE
                    tvNote.visibility = View.GONE
                    listState.show(0, state.message)
                }
                is TimetableState.Ready -> {
                    val timetable = state.timetable
                    tvWeek.text = "Tuần ${timetable.days.first().date} – ${timetable.days.last().date}"
                    val visibility = if (timetable.isEmpty) View.GONE else View.VISIBLE
                    grid.visibility = visibility
                    tvNote.visibility = visibility
                    listState.show(
                        timetable.slots.size,
                        if (role == Role.TEACHER) {
                            "Bạn chưa được phân công lớp học nào có lịch cụ thể."
                        } else {
                            "Bạn chưa được xếp vào lớp học nào có lịch cụ thể."
                        }
                    )
                    if (!timetable.isEmpty) showGrid(grid, timetable)
                }
            }
        }

        viewModel.load(userId, role)

        return view
    }

    private fun showGrid(grid: LinearLayout, timetable: WeekTimetable) {
        grid.removeAllViews()
        grid.addView(headerRow(timetable.slots))
        for (day in timetable.days) {
            grid.addView(divider())
            grid.addView(dayRow(grid, day, timetable.slots))
        }
    }

    // Start and end time of every slot, above its column
    private fun headerRow(slots: List<String>): View {
        val context = requireContext()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), dp(4), dp(8))
        }
        row.addView(
            View(context),
            LinearLayout.LayoutParams(resources.getDimensionPixelSize(R.dimen.timetable_day_width), 0)
        )
        for (slot in slots) {
            row.addView(
                TextView(context).apply {
                    text = slot.replace("-", "\n")
                    gravity = Gravity.CENTER
                    setTextAppearance(R.style.TextAppearance_Kstudy_Caption)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        return row
    }

    private fun dayRow(grid: ViewGroup, day: TimetableDay, slots: List<String>): View {
        val row = layoutInflater.inflate(R.layout.item_timetable_day, grid, false)
        val tvName = row.findViewById<TextView>(R.id.tvTimetableDayName)
        val tvDate = row.findViewById<TextView>(R.id.tvTimetableDayDate)
        tvName.text = day.name
        tvDate.text = if (day.isToday) "Hôm nay" else day.date
        if (day.isToday) {
            val primary = ContextCompat.getColor(requireContext(), R.color.primary)
            tvName.setTextColor(primary)
            tvDate.setTextColor(primary)
        }

        val cells = row.findViewById<LinearLayout>(R.id.layoutTimetableCells)
        for (slot in slots) {
            // Two classes at the same time are a clash in the data; both are shown, one under the other
            val cell = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            for (lesson in day.lessonsAt(slot)) {
                cell.addView(lessonView(cell, day, lesson))
            }
            cells.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    private fun lessonView(cell: ViewGroup, day: TimetableDay, lesson: Lesson): View {
        val item = layoutInflater.inflate(R.layout.item_timetable_lesson, cell, false)
        item.findViewById<TextView>(R.id.tvLessonName).text = lesson.courseClass.name
        item.findViewById<TextView>(R.id.tvLessonRoom).text = lesson.courseClass.room
        item.contentDescription = "${lesson.courseClass.name}, ${day.name}, ${lesson.slot}"
        item.setOnClickListener { showLesson(day, lesson) }
        return item
    }

    private fun showLesson(day: TimetableDay, lesson: Lesson) {
        val courseClass = lesson.courseClass
        val lines = mutableListOf(
            "${day.name}, ${lesson.slot.replace("-", " – ")}",
            roomLabel(courseClass.room)
        )
        // A student wants to know who teaches, a teacher how many students to expect
        if (role == Role.TEACHER) {
            lines.add("Sĩ số: ${courseClass.totalStudents} học sinh")
        } else if (courseClass.teacherName.isNotBlank()) {
            lines.add("Giáo viên: ${courseClass.teacherName}")
        }
        if (courseClass.academicYear.isNotBlank()) {
            lines.add("Năm học: ${courseClass.academicYear}")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(courseClass.name)
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton("Đóng", null)
            .show()
    }

    // The room is free text: some classes hold "101", others "Phòng 101"
    private fun roomLabel(room: String): String =
        if (room.startsWith("Phòng", ignoreCase = true)) room else "Phòng $room"

    private fun divider(): View =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.outline_variant))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
