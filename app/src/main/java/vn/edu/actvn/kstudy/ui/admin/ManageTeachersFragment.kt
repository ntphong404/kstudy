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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Teacher
import vn.edu.actvn.kstudy.ui.adapter.TeacherAdapter
import vn.edu.actvn.kstudy.viewmodel.AdminViewModel
import java.util.*

class ManageTeachersFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: TeacherAdapter
    private var allTeachers = listOf<Teacher>()

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

        viewModel.teachers.observe(viewLifecycleOwner) { teachers ->
            allTeachers = teachers
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
    }

    private fun showAddDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_teacher, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvTitle.text = "Thêm Giáo viên mới"

        val etUser = dialogView.findViewById<EditText>(R.id.etTeacherUser)
        val etPass = dialogView.findViewById<EditText>(R.id.etTeacherPass)
        val etName = dialogView.findViewById<EditText>(R.id.etTeacherName)
        val etSub = dialogView.findViewById<EditText>(R.id.etTeacherSub)
        val etPhone = dialogView.findViewById<EditText>(R.id.etTeacherPhone)
        
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTeacher)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveTeacher)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString().trim()
            val name = etName.text.toString().trim()
            val sub = etSub.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty() || name.isEmpty() || sub.isEmpty() || phone.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addTeacher(
                username = user,
                pass = pass,
                fullName = name,
                subject = sub,
                phone = phone
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
        tvTitle.text = "Sửa thông tin Giáo viên"

        // Hide username and password fields when editing
        dialogView.findViewById<View>(R.id.tilUsername).visibility = View.GONE
        dialogView.findViewById<View>(R.id.tilPassword).visibility = View.GONE

        val etName = dialogView.findViewById<EditText>(R.id.etTeacherName)
        val etSub = dialogView.findViewById<EditText>(R.id.etTeacherSub)
        val etPhone = dialogView.findViewById<EditText>(R.id.etTeacherPhone)
        
        etName.setText(teacher.name)
        etSub.setText(teacher.subject)
        etPhone.setText(teacher.phone)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelTeacher)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveTeacher)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val sub = etSub.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isEmpty() || sub.isEmpty() || phone.isEmpty()) {
                Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.updateTeacher(
                userId = teacher.userId,
                fullName = name,
                subject = sub,
                phone = phone
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
