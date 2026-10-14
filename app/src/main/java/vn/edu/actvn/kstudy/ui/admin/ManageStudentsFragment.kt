package vn.edu.actvn.kstudy.ui.admin

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Student
import vn.edu.actvn.kstudy.ui.adapter.StudentAdapter
import vn.edu.actvn.kstudy.ui.common.FormFields
import vn.edu.actvn.kstudy.ui.common.ListState
import vn.edu.actvn.kstudy.ui.common.PersonalFields
import vn.edu.actvn.kstudy.ui.common.ResetPasswordDialog
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel
import java.util.*

class ManageStudentsFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: StudentAdapter
    private var allStudents = listOf<Student>()
    private lateinit var listState: ListState
    private var loaded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_manage_students, container, false)

        viewModel = ViewModelProvider(this)[AdminViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvStudents)
        rv.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = StudentAdapter(
            list = emptyList(),
            onEditClick = { student -> showEditDialog(student) },
            onDeleteClick = { student -> showDeleteConfirm(student) },
            onActionClick = { student ->
                ResetPasswordDialog.show(requireContext(), student.name) { newPassword ->
                    viewModel.resetPassword(student.userId, student.name, newPassword)
                }
            },
            actionText = "Đặt lại mật khẩu"
        )
        rv.adapter = adapter

        val etSearch = view.findViewById<EditText>(R.id.etSearchStudent)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val btnAdd = view.findViewById<Button>(R.id.btnAddStudent)
        btnAdd.setOnClickListener { showAddMenu(btnAdd) }

        listState = ListState(view)

        viewModel.students.observe(viewLifecycleOwner) { students ->
            allStudents = students
            loaded = true
            filterList(etSearch.text.toString())
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { (success, message) ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        viewModel.loadStudents()

        return view
    }

    private fun filterList(query: String) {
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = allStudents.filter {
            it.name.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    it.studentCode.lowercase(Locale.getDefault()).contains(lowerQuery) ||
                    it.className.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
        adapter.updateList(filtered)
        // Typing in the search box before the list has arrived must not end the loading state
        if (loaded) {
            listState.show(
                filtered.size,
                if (query.isBlank()) "Chưa có học sinh nào." else "Không tìm thấy học sinh phù hợp."
            )
        }
    }

    // One student through the form, or a whole class list from a file
    private fun showAddMenu(anchor: View) {
        val menu = PopupMenu(requireContext(), anchor)
        menu.menu.add("Thêm một học sinh").setOnMenuItemClickListener {
            showAddDialog()
            true
        }
        menu.menu.add("Nhập từ file Excel/CSV").setOnMenuItemClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.adminFragmentContainer, ImportStudentsFragment())
                .addToBackStack(null)
                .commit()
            true
        }
        menu.show()
    }

    private fun showAddDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_student, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Thêm học sinh mới"

        // These fields are required; an empty one is marked where it is. The major and the
        // personal details may stay empty.
        val fields = FormFields.required(
            dialogView, R.id.tilUsername, R.id.tilPassword,
            R.id.tilStudentName, R.id.tilStudentCode, R.id.tilStudentClass, R.id.tilStudentPhone
        )

        val etUser = dialogView.findViewById<EditText>(R.id.etStudentUser)
        val etPass = dialogView.findViewById<EditText>(R.id.etStudentPass)
        val etName = dialogView.findViewById<EditText>(R.id.etStudentName)
        val etCode = dialogView.findViewById<EditText>(R.id.etStudentCode)
        val etClass = dialogView.findViewById<EditText>(R.id.etStudentClass)
        val etMajor = dialogView.findViewById<EditText>(R.id.etStudentMajor)
        val etPhone = dialogView.findViewById<EditText>(R.id.etStudentPhone)
        val personalFields = PersonalFields(dialogView)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelStudent)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveStudent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val name = etName.text.toString().trim()
            val code = etCode.text.toString().trim()
            val className = etClass.text.toString().trim()
            val major = etMajor.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // Both checks run, so that every problem is marked at once
            val filled = FormFields.requireFilled(*fields)
            val personalValid = personalFields.validate()
            if (!filled || !personalValid) {
                return@setOnClickListener
            }

            viewModel.addStudent(
                username = user,
                pass = pass,
                student = Student(
                    name = name,
                    studentCode = code,
                    className = className,
                    major = major,
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

    private fun showEditDialog(student: Student) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_student, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Sửa thông tin học sinh"

        val fields = FormFields.required(
            dialogView, R.id.tilStudentName, R.id.tilStudentCode, R.id.tilStudentClass, R.id.tilStudentPhone
        )

        // Hide username and password when editing
        dialogView.findViewById<View>(R.id.tilUsername).visibility = View.GONE
        dialogView.findViewById<View>(R.id.tilPassword).visibility = View.GONE

        val etName = dialogView.findViewById<EditText>(R.id.etStudentName)
        val etCode = dialogView.findViewById<EditText>(R.id.etStudentCode)
        val etClass = dialogView.findViewById<EditText>(R.id.etStudentClass)
        val etMajor = dialogView.findViewById<EditText>(R.id.etStudentMajor)
        val etPhone = dialogView.findViewById<EditText>(R.id.etStudentPhone)
        val personalFields = PersonalFields(dialogView)

        etName.setText(student.name)
        etCode.setText(student.studentCode)
        etClass.setText(student.className)
        etMajor.setText(student.major)
        etPhone.setText(student.phone)
        personalFields.show(student.personal)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelStudent)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveStudent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val code = etCode.text.toString().trim()
            val className = etClass.text.toString().trim()
            val major = etMajor.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // Both checks run, so that every problem is marked at once
            val filled = FormFields.requireFilled(*fields)
            val personalValid = personalFields.validate()
            if (!filled || !personalValid) {
                return@setOnClickListener
            }

            viewModel.updateStudent(
                student.copy(
                    name = name,
                    studentCode = code,
                    className = className,
                    major = major,
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

    private fun showDeleteConfirm(student: Student) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa học sinh ${student.name} khỏi hệ thống không?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteStudent(student.userId)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
