package vn.edu.actvn.kstudy.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.CourseClass
import vn.edu.actvn.kstudy.ui.adapter.ClassAdapter
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel
import java.util.*

class ManageClassesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: ClassAdapter
    private var allClasses = listOf<CourseClass>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_manage_classes, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvClasses)
        rv.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = ClassAdapter(
            list = emptyList(),
            onEditClick = { courseClass -> showEditDialog(courseClass) },
            onDeleteClick = { courseClass -> showDeleteConfirm(courseClass) },
            onRosterClick = { courseClass ->
                val fragment = ClassRosterFragment.newInstance(courseClass.id, courseClass.name, courseClass.academicYear)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.adminFragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )
        rv.adapter = adapter

        val etSearch = view.findViewById<EditText>(R.id.etSearchClass)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val btnAdd = view.findViewById<Button>(R.id.btnAddClass)
        btnAdd.setOnClickListener { showAddDialog() }

        viewModel.classes.observe(viewLifecycleOwner) { classes ->
            allClasses = classes
            filterList(etSearch.text.toString())
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadClasses()
        viewModel.loadTeachers()

        return view
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun filterList(query: String) {
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = allClasses.filter {
            it.name.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    it.academicYear.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
        adapter.updateList(filtered)
    }

    private fun showAddDialog() {
        val context = requireContext()
        val teachers = viewModel.teachers.value ?: emptyList()
        if (teachers.isEmpty()) {
            Toast.makeText(context, "Vui lòng thêm giáo viên trước khi tạo lớp!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_class, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Thêm Lớp học mới"

        val etName = dialogView.findViewById<EditText>(R.id.etClassName)
        val etAcademic = dialogView.findViewById<EditText>(R.id.etAcademicYear)
        val etRoom = dialogView.findViewById<EditText>(R.id.etRoom)
        val actvTimeSlot = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTimeSlot)
        val etDays = dialogView.findViewById<EditText>(R.id.etDays)
        val btnSelectTeacher = dialogView.findViewById<Button>(R.id.btnSelectTeacherDialog)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelClass)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveClass)

        // Time slot dropdown setup
        val slots = arrayOf("07:30-09:00", "09:30-11:00", "13:30-15:30", "15:30-17:00", "04:00-05:30") // TEMP TEST SLOT - remove before submission
        val adapterTime = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, slots)
        actvTimeSlot.setAdapter(adapterTime)
        actvTimeSlot.setOnClickListener {
            hideKeyboard(actvTimeSlot)
            actvTimeSlot.showDropDown()
        }
        actvTimeSlot.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) hideKeyboard(actvTimeSlot)
        }

        // Days multi-select dialog setup
        val daysArray = arrayOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật")
        val selectedDaysBoolean = BooleanArray(daysArray.size) { false }
        val selectedDaysList = mutableListOf<String>()

        etDays.setOnClickListener {
            hideKeyboard(etDays)
            AlertDialog.Builder(context)
                .setTitle("Chọn ngày học")
                .setMultiChoiceItems(daysArray, selectedDaysBoolean) { _, index, isChecked ->
                    selectedDaysBoolean[index] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    selectedDaysList.clear()
                    for (i in daysArray.indices) {
                        if (selectedDaysBoolean[i]) {
                            selectedDaysList.add(daysArray[i])
                        }
                    }
                    etDays.setText(selectedDaysList.joinToString(", "))
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Teacher selector
        var selectedTeacherId = teachers[0].id
        btnSelectTeacher.text = "Chọn Giáo viên: ${teachers[0].name}"
        btnSelectTeacher.setOnClickListener {
            hideKeyboard(btnSelectTeacher)
            val teacherNames = teachers.map { "${it.name} (${it.subject})" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Chọn Giáo viên phụ trách")
                .setItems(teacherNames) { _, index ->
                    selectedTeacherId = teachers[index].id
                    btnSelectTeacher.text = "Chọn Giáo viên: ${teachers[index].name}"
                }
                .show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val academicYear = etAcademic.text.toString().trim()
            val room = etRoom.text.toString().trim()
            val timeSlot = actvTimeSlot.text.toString().trim()
            val daysStr = etDays.text.toString().trim()

            if (name.isEmpty() || academicYear.isEmpty() || room.isEmpty() || timeSlot.isEmpty() || daysStr.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = "$daysStr ($timeSlot)"

            viewModel.addClass(
                name = name,
                academicYear = academicYear,
                teacherId = selectedTeacherId,
                room = room,
                schedule = schedule
            )
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEditDialog(courseClass: CourseClass) {
        val context = requireContext()
        val teachers = viewModel.teachers.value ?: emptyList()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_class, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Sửa thông tin Lớp học"

        val etName = dialogView.findViewById<EditText>(R.id.etClassName)
        val etAcademic = dialogView.findViewById<EditText>(R.id.etAcademicYear)
        val etRoom = dialogView.findViewById<EditText>(R.id.etRoom)
        val actvTimeSlot = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTimeSlot)
        val etDays = dialogView.findViewById<EditText>(R.id.etDays)
        val btnSelectTeacher = dialogView.findViewById<Button>(R.id.btnSelectTeacherDialog)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelClass)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveClass)

        etName.setText(courseClass.name)
        etAcademic.setText(courseClass.academicYear)
        etRoom.setText(courseClass.room)

        // Time slot dropdown setup
        val slots = arrayOf("07:30-09:00", "09:30-11:00", "13:30-15:30", "15:30-17:00", "04:00-05:30") // TEMP TEST SLOT - remove before submission
        val adapterTime = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, slots)
        actvTimeSlot.setAdapter(adapterTime)
        actvTimeSlot.setOnClickListener {
            hideKeyboard(actvTimeSlot)
            actvTimeSlot.showDropDown()
        }
        actvTimeSlot.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) hideKeyboard(actvTimeSlot)
        }

        val scheduleStr = courseClass.schedule
        var activeSlot = ""
        for (slot in slots) {
            if (scheduleStr.contains(slot)) {
                activeSlot = slot
                break
            }
        }
        actvTimeSlot.setText(activeSlot, false)

        // Days multi-select dialog setup
        val daysArray = arrayOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ Nhật")
        val selectedDaysBoolean = BooleanArray(daysArray.size) { false }
        val selectedDaysList = mutableListOf<String>()

        for (i in daysArray.indices) {
            if (scheduleStr.contains(daysArray[i])) {
                selectedDaysBoolean[i] = true
                selectedDaysList.add(daysArray[i])
            }
        }
        etDays.setText(selectedDaysList.joinToString(", "))

        etDays.setOnClickListener {
            hideKeyboard(etDays)
            AlertDialog.Builder(context)
                .setTitle("Chọn ngày học")
                .setMultiChoiceItems(daysArray, selectedDaysBoolean) { _, index, isChecked ->
                    selectedDaysBoolean[index] = isChecked
                }
                .setPositiveButton("OK") { _, _ ->
                    selectedDaysList.clear()
                    for (i in daysArray.indices) {
                        if (selectedDaysBoolean[i]) {
                            selectedDaysList.add(daysArray[i])
                        }
                    }
                    etDays.setText(selectedDaysList.joinToString(", "))
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        // Find current teacher
        var selectedTeacherId = courseClass.teacherId
        val currentTeacher = teachers.find { it.id == selectedTeacherId }
        btnSelectTeacher.text = "Giáo viên: ${currentTeacher?.name ?: "Chưa phân công"}"
        btnSelectTeacher.setOnClickListener {
            hideKeyboard(btnSelectTeacher)
            if (teachers.isEmpty()) {
                Toast.makeText(context, "Không tìm thấy giáo viên!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val teacherNames = teachers.map { "${it.name} (${it.subject})" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle("Chọn Giáo viên phụ trách")
                .setItems(teacherNames) { _, index ->
                    selectedTeacherId = teachers[index].id
                    btnSelectTeacher.text = "Giáo viên: ${teachers[index].name}"
                }
                .show()
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val academicYear = etAcademic.text.toString().trim()
            val room = etRoom.text.toString().trim()
            val timeSlot = actvTimeSlot.text.toString().trim()
            val daysStr = etDays.text.toString().trim()

            if (name.isEmpty() || academicYear.isEmpty() || room.isEmpty() || timeSlot.isEmpty() || daysStr.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val schedule = "$daysStr ($timeSlot)"

            viewModel.updateClass(
                classId = courseClass.id,
                name = name,
                academicYear = academicYear,
                teacherId = selectedTeacherId,
                room = room,
                schedule = schedule
            )
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showDeleteConfirm(courseClass: CourseClass) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa lớp học ${courseClass.name} - ${courseClass.academicYear} không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteClass(courseClass.id)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
