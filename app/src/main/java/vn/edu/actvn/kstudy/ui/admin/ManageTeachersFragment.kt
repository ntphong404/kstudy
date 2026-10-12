package vn.edu.actvn.kstudy.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Teacher
import vn.edu.actvn.kstudy.ui.adapter.TeacherAdapter
import vn.edu.actvn.kstudy.ui.common.FormFields
import vn.edu.actvn.kstudy.ui.common.ListState
import vn.edu.actvn.kstudy.ui.common.PersonalFields
import vn.edu.actvn.kstudy.util.ProfileRules
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel
import java.util.*

class ManageTeachersFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: TeacherAdapter
    private var allTeachers = listOf<Teacher>()
    private lateinit var listState: ListState
    private var loaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_manage_teachers, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvTeachers)
        rv.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = TeacherAdapter(
            list = emptyList(),
            onEditClick = { teacher -> showEditDialog(teacher) },
            onDeleteClick = { teacher -> showDeleteConfirm(teacher) }
        )
        rv.adapter = adapter

        val etSearch = view.findViewById<EditText>(R.id.etSearchTeacher)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val btnAdd = view.findViewById<Button>(R.id.btnAddTeacher)
        btnAdd.setOnClickListener { showAddDialog() }

        listState = ListState(view)

        viewModel.teachers.observe(viewLifecycleOwner) { teachers ->
            allTeachers = teachers
            loaded = true
            filterList(etSearch.text.toString())
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadTeachers()

        return view
    }

    private fun filterList(query: String) {
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = allTeachers.filter {
            it.name.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    it.subject.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
        adapter.updateList(filtered)
        // Typing in the search box before the list has arrived must not end the loading state
        if (loaded) {
            listState.show(
                filtered.size,
                if (query.isBlank()) "Chưa có giáo viên nào." else "Không tìm thấy giáo viên phù hợp."
            )
        }
    }

    private fun showAddDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_teacher, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Thêm giáo viên mới"

        // These fields are required; an empty one is marked where it is. The department, the
        // degree and the personal details may stay empty.
        val fields = FormFields.required(
            dialogView, R.id.tilUsername, R.id.tilPassword,
            R.id.tilTeacherName, R.id.tilTeacherSub, R.id.tilTeacherPhone
        )

        val etUser = dialogView.findViewById<EditText>(R.id.etTeacherUser)
        val etPass = dialogView.findViewById<EditText>(R.id.etTeacherPass)
        val etName = dialogView.findViewById<EditText>(R.id.etTeacherName)
        val etSub = dialogView.findViewById<EditText>(R.id.etTeacherSub)
        val etDepartment = dialogView.findViewById<EditText>(R.id.etTeacherDepartment)
        val actvDegree = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTeacherDegree)
        val etPhone = dialogView.findViewById<EditText>(R.id.etTeacherPhone)
        val personalFields = PersonalFields(dialogView)
        FormFields.setChoices(actvDegree, ProfileRules.DEGREES)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTeacher)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveTeacher)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val name = etName.text.toString().trim()
            val sub = etSub.text.toString().trim()
            val department = etDepartment.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // Both checks run, so that every problem is marked at once
            val filled = FormFields.requireFilled(*fields)
            val personalValid = personalFields.validate()
            if (!filled || !personalValid) {
                return@setOnClickListener
            }

            viewModel.addTeacher(
                username = user,
                pass = pass,
                teacher = Teacher(
                    name = name,
                    subject = sub,
                    department = department,
                    degree = actvDegree.text.toString(),
                    phone = phone,
                    personal = personalFields.read()
                )
            )
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEditDialog(teacher: Teacher) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_teacher, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Sửa thông tin giáo viên"

        val fields = FormFields.required(dialogView, R.id.tilTeacherName, R.id.tilTeacherSub, R.id.tilTeacherPhone)

        // Hide username and password fields when editing
        dialogView.findViewById<View>(R.id.tilUsername).visibility = View.GONE
        dialogView.findViewById<View>(R.id.tilPassword).visibility = View.GONE

        val etName = dialogView.findViewById<EditText>(R.id.etTeacherName)
        val etSub = dialogView.findViewById<EditText>(R.id.etTeacherSub)
        val etDepartment = dialogView.findViewById<EditText>(R.id.etTeacherDepartment)
        val actvDegree = dialogView.findViewById<AutoCompleteTextView>(R.id.actvTeacherDegree)
        val etPhone = dialogView.findViewById<EditText>(R.id.etTeacherPhone)
        val personalFields = PersonalFields(dialogView)
        FormFields.setChoices(actvDegree, ProfileRules.DEGREES)

        etName.setText(teacher.name)
        etSub.setText(teacher.subject)
        etDepartment.setText(teacher.department)
        // Without filtering, otherwise the dropdown would only offer the degree it already shows
        actvDegree.setText(teacher.degree, false)
        etPhone.setText(teacher.phone)
        personalFields.show(teacher.personal)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTeacher)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveTeacher)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val sub = etSub.text.toString().trim()
            val department = etDepartment.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // Both checks run, so that every problem is marked at once
            val filled = FormFields.requireFilled(*fields)
            val personalValid = personalFields.validate()
            if (!filled || !personalValid) {
                return@setOnClickListener
            }

            viewModel.updateTeacher(
                teacher.copy(
                    name = name,
                    subject = sub,
                    department = department,
                    degree = actvDegree.text.toString(),
                    phone = phone,
                    personal = personalFields.read()
                )
            )
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showDeleteConfirm(teacher: Teacher) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa giáo viên ${teacher.name} khỏi hệ thống không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteTeacher(teacher.userId)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
