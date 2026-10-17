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
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.util.Lesson
import vn.edu.actvn.kstudy.util.ScheduleUtils
import vn.edu.actvn.kstudy.util.Timetable
import vn.edu.actvn.kstudy.util.TimetableDay
import vn.edu.actvn.kstudy.util.TimetableMode
import vn.edu.actvn.kstudy.viewmodel.KstudyViewModelFactory
import vn.edu.actvn.kstudy.viewmodel.TimetableState
import vn.edu.actvn.kstudy.viewmodel.TimetableViewModel

// The timetable of a student or a teacher, by day, week or month:
// - week: a grid with one row per day and one column per time slot
// - month: a calendar with a dot on the days that have lessons; the lessons of the picked day are listed under it
// - day: the lessons of the day, one row per time slot
class TimetableFragment : Fragment() {

    private var userId: String = ""
    private var role: Role = Role.STUDENT

    private lateinit var viewModel: TimetableViewModel

    companion object {
        private val WEEKDAY_SHORT_NAMES = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

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

        val btnToday = view.findViewById<View>(R.id.btnTimetableToday)
        val controls = view.findViewById<View>(R.id.layoutTimetableControls)
        val content = view.findViewById<View>(R.id.layoutTimetableContent)
        val listState = ListState(view)

        view.findViewById<View>(R.id.btnBackTimetable).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<View>(R.id.btnTimetablePrevious).setOnClickListener { viewModel.previous() }
        view.findViewById<View>(R.id.btnTimetableNext).setOnClickListener { viewModel.next() }
        btnToday.setOnClickListener { viewModel.goToToday() }
        view.findViewById<MaterialButtonToggleGroup>(R.id.toggleTimetableMode)
            .addOnButtonCheckedListener { _, buttonId, isChecked ->
                if (isChecked) viewModel.setMode(modeOf(buttonId))
            }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is TimetableState.Loading -> Unit
                is TimetableState.Error -> {
                    btnToday.visibility = View.GONE
                    controls.visibility = View.GONE
                    content.visibility = View.GONE
                    listState.show(0, state.message)
                }
                is TimetableState.Ready -> {
                    val timetable = state.timetable
                    val visibility = if (timetable.isEmpty) View.GONE else View.VISIBLE
                    btnToday.visibility = visibility
                    controls.visibility = visibility
                    content.visibility = visibility
                    listState.show(
                        timetable.slots.size,
                        if (role == Role.TEACHER) {
                            "Bạn chưa được phân công lớp học nào có lịch cụ thể."
                        } else {
                            "Bạn chưa được xếp vào lớp học nào có lịch cụ thể."
                        }
                    )
                    if (!timetable.isEmpty) show(view, timetable)
                }
            }
        }

        viewModel.load(userId, role)

        return view
    }

    private fun show(root: View, timetable: Timetable) {
        val mode = timetable.mode
        val toggle = root.findViewById<MaterialButtonToggleGroup>(R.id.toggleTimetableMode)
        // Checking the button calls the listener again, which ignores a mode that is already shown
        if (toggle.checkedButtonId != buttonOf(mode)) toggle.check(buttonOf(mode))

        root.findViewById<TextView>(R.id.tvTimetablePeriod).text = timetable.title
        root.findViewById<View>(R.id.btnTimetableToday).isEnabled = !timetable.selected.isToday

        val grid = root.findViewById<LinearLayout>(R.id.layoutTimetableGrid)
        val month = root.findViewById<LinearLayout>(R.id.layoutTimetableMonth)
        val dayTitle = root.findViewById<TextView>(R.id.tvTimetableDayTitle)
        val lessons = root.findViewById<LinearLayout>(R.id.layoutTimetableLessons)
        grid.visibility = if (mode == TimetableMode.WEEK) View.VISIBLE else View.GONE
        month.visibility = if (mode == TimetableMode.MONTH) View.VISIBLE else View.GONE
        dayTitle.visibility = month.visibility
        lessons.visibility = if (mode == TimetableMode.WEEK) View.GONE else View.VISIBLE

        when (mode) {
            TimetableMode.WEEK -> showWeek(grid, timetable)
            TimetableMode.MONTH -> {
                showMonth(month, timetable)
                val selected = timetable.selected
                dayTitle.text = "${selected.name}, ${selected.fullDate}" + if (selected.isToday) " (Hôm nay)" else ""
                showDay(lessons, selected)
            }
            TimetableMode.DAY -> showDay(lessons, timetable.selected)
        }

        val note = root.findViewById<TextView>(R.id.tvTimetableNote)
        note.text = if (mode == TimetableMode.MONTH) {
            "Chạm vào một ngày để xem các buổi học của ngày đó."
        } else {
            "Chạm vào một buổi học để xem chi tiết."
        }
        // A day without lessons has nothing to tap
        val nothingToTap = mode == TimetableMode.DAY && timetable.selected.lessons.isEmpty()
        note.visibility = if (nothingToTap) View.GONE else View.VISIBLE
    }

    private fun showWeek(grid: LinearLayout, timetable: Timetable) {
        grid.removeAllViews()
        grid.addView(headerRow(timetable.slots))
        for (day in timetable.days) {
            grid.addView(divider())
            grid.addView(
                row(
                    grid, day.name, if (day.isToday) "Hôm nay" else day.date, day.isToday,
                    day, timetable.slots.map { day.lessonsAt(it) }
                )
            )
        }
    }

    // One row per time slot in which the day has a lesson
    private fun showDay(list: LinearLayout, day: TimetableDay) {
        list.removeAllViews()
        val slots = day.lessons.map { it.slot }.distinct()
        if (slots.isEmpty()) {
            list.addView(
                TextView(requireContext()).apply {
                    text = "Không có buổi học nào trong ngày này."
                    setTextAppearance(R.style.TextAppearance_Kstudy_Body_Secondary)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
            )
            return
        }
        slots.forEachIndexed { index, slot ->
            if (index > 0) list.addView(divider())
            val (start, end) = slot.split("-")
            list.addView(row(list, start, end, false, day, listOf(day.lessonsAt(slot))))
        }
    }

    private fun showMonth(month: LinearLayout, timetable: Timetable) {
        val context = requireContext()
        month.removeAllViews()
        month.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(4))
                for (name in WEEKDAY_SHORT_NAMES) {
                    addView(
                        TextView(context).apply {
                            text = name
                            gravity = Gravity.CENTER
                            setTextAppearance(R.style.TextAppearance_Kstudy_Caption)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    )
                }
            }
        )

        // Blank cells before the 1st put every day under its day of the week, and after the last
        // one they keep the cells of the last row as wide as the others
        val cells: List<TimetableDay?> = List(timetable.days.first().weekday) { null } + timetable.days
        for (week in cells.chunked(7)) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (day in week + List(7 - week.size) { null }) {
                if (day == null) {
                    row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
                } else {
                    row.addView(monthDay(row, day, isSelected = day == timetable.selected))
                }
            }
            month.addView(row)
        }
    }

    private fun monthDay(row: ViewGroup, day: TimetableDay, isSelected: Boolean): View {
        val context = requireContext()
        val cell = layoutInflater.inflate(R.layout.item_timetable_month_day, row, false)
        val tvNumber = cell.findViewById<TextView>(R.id.tvMonthDayNumber)
        tvNumber.text = day.dayOfMonth.toString()
        when {
            isSelected -> {
                tvNumber.setBackgroundResource(R.drawable.bg_timetable_day_selected)
                tvNumber.setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            }
            day.isToday -> {
                tvNumber.setBackgroundResource(R.drawable.bg_timetable_day_today)
                tvNumber.setTextColor(ContextCompat.getColor(context, R.color.primary))
            }
        }
        cell.findViewById<View>(R.id.viewMonthDayDot).visibility =
            if (day.lessons.isEmpty()) View.INVISIBLE else View.VISIBLE

        cell.isSelected = isSelected
        cell.contentDescription = buildString {
            append("${day.name}, ${day.fullDate}")
            if (day.isToday) append(", hôm nay")
            append(if (day.lessons.isEmpty()) ", không có buổi học" else ", ${day.lessons.size} buổi học")
        }
        cell.setOnClickListener { viewModel.selectDay(day) }
        return cell
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

    // A label in the first column (the day, or the start and end of a time slot), then one cell per slot
    private fun row(
        parent: ViewGroup,
        label: String,
        sublabel: String,
        highlight: Boolean,
        day: TimetableDay,
        cells: List<List<Lesson>>
    ): View {
        val row = layoutInflater.inflate(R.layout.item_timetable_day, parent, false)
        val tvName = row.findViewById<TextView>(R.id.tvTimetableDayName)
        val tvDate = row.findViewById<TextView>(R.id.tvTimetableDayDate)
        tvName.text = label
        tvDate.text = sublabel
        if (highlight) {
            val primary = ContextCompat.getColor(requireContext(), R.color.primary)
            tvName.setTextColor(primary)
            tvDate.setTextColor(primary)
        }

        val cellsLayout = row.findViewById<LinearLayout>(R.id.layoutTimetableCells)
        for (lessons in cells) {
            // Two classes at the same time are a clash in the data; both are shown, one under the other
            val cell = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            for (lesson in lessons) {
                cell.addView(lessonView(cell, day, lesson))
            }
            cellsLayout.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return row
    }

    private fun lessonView(cell: ViewGroup, day: TimetableDay, lesson: Lesson): View {
        val item = layoutInflater.inflate(R.layout.item_timetable_lesson, cell, false)
        item.findViewById<TextView>(R.id.tvLessonName).text = lesson.courseClass.name
        item.findViewById<TextView>(R.id.tvLessonRoom).text = lesson.courseClass.room
        item.contentDescription = "${lesson.courseClass.name}, ${day.name} ${day.fullDate}, ${lesson.slot}"
        item.setOnClickListener { showLesson(day, lesson) }
        return item
    }

    private fun showLesson(day: TimetableDay, lesson: Lesson) {
        val courseClass = lesson.courseClass
        val lines = mutableListOf(
            "${day.name}, ${day.fullDate}, ${lesson.slot.replace("-", " – ")}",
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
        val dates = ScheduleUtils.datesLabel(courseClass)
        if (dates.isNotEmpty()) {
            lines.add("Thời gian học: $dates")
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

    private fun modeOf(buttonId: Int): TimetableMode = when (buttonId) {
        R.id.btnModeDay -> TimetableMode.DAY
        R.id.btnModeMonth -> TimetableMode.MONTH
        else -> TimetableMode.WEEK
    }

    private fun buttonOf(mode: TimetableMode): Int = when (mode) {
        TimetableMode.DAY -> R.id.btnModeDay
        TimetableMode.WEEK -> R.id.btnModeWeek
        TimetableMode.MONTH -> R.id.btnModeMonth
    }

    private fun divider(): View =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.outline_variant))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
