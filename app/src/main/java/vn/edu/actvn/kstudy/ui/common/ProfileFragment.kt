package vn.edu.actvn.kstudy.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.data.model.User
import vn.edu.actvn.kstudy.data.notification.NotificationCenter
import vn.edu.actvn.kstudy.data.repository.PasswordChange
import vn.edu.actvn.kstudy.ui.LoginActivity
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class ProfileFragment : Fragment() {

    private lateinit var authViewModel: AuthViewModel

    // The open "change password" dialog, kept to show it the answer of the server
    private var passwordDialog: ChangePasswordDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        saved: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        val tvInitials = view.findViewById<TextView>(R.id.tvProfileInitials)
        val tvFullName = view.findViewById<TextView>(R.id.tvProfileFullName)
        val tvUsername = view.findViewById<TextView>(R.id.tvProfileUsername)
        val tvRole = view.findViewById<TextView>(R.id.tvProfileRole)
        val tvRoleInfoHeader = view.findViewById<TextView>(R.id.tvProfileRoleInfoHeader)
        val layoutRoleInfo = view.findViewById<ViewGroup>(R.id.layoutProfileRoleInfo)
        val etName = view.findViewById<EditText>(R.id.etProfileName)
        val etPhone = view.findViewById<EditText>(R.id.etProfilePhone)
        val personalFields = PersonalFields(view)
        val btnUpdate = view.findViewById<Button>(R.id.btnUpdateProfile)
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)

        authViewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                tvFullName.text = user.fullName
                tvUsername.text = "@${user.username}"
                tvRole.text = when (user.role.name) {
                    "ADMIN" -> "Quản trị viên"
                    "TEACHER" -> "Giáo viên giảng dạy"
                    "STUDENT" -> "Học sinh / Sinh viên"
                    else -> user.role.name
                }
                etName.setText(user.fullName)
                etPhone.setText(user.phone)
                personalFields.show(user.personal)
                showRoleInfo(tvRoleInfoHeader, layoutRoleInfo, user)

                // initials
                val initials = if (user.fullName.isNotBlank()) {
                    val parts = user.fullName.split(" ")
                    if (parts.size >= 2) {
                        "${parts[parts.size - 2].take(1)}${parts.last().take(1)}"
                    } else {
                        user.fullName.take(2)
                    }
                } else {
                    "ND"
                }
                tvInitials.text = initials.uppercase()
            }
        }

        authViewModel.profileResult.observe(viewLifecycleOwner) { result ->
            if (result != null) {
                Toast.makeText(requireContext(), result.second, Toast.LENGTH_SHORT).show()
                authViewModel.clearProfileResult()
            }
        }

        // The name is the only required field; the view model rejects an empty one as well
        val nameField = FormFields.required(view, R.id.tilProfileName)

        btnUpdate.setOnClickListener {
            // Both checks run, so that every problem is marked at once
            val nameFilled = FormFields.requireFilled(*nameField)
            val personalValid = personalFields.validate()
            if (!nameFilled || !personalValid) return@setOnClickListener
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            authViewModel.updateProfile(name, phone, personalFields.read())
        }

        view.findViewById<Button>(R.id.btnChangePassword).setOnClickListener {
            passwordDialog = ChangePasswordDialog(requireContext()) { currentPassword, newPassword ->
                authViewModel.changePassword(currentPassword, newPassword)
            }.also { it.show() }
        }

        authViewModel.passwordResult.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            authViewModel.clearPasswordResult()
            when (result) {
                PasswordChange.CHANGED -> {
                    passwordDialog?.dismiss()
                    passwordDialog = null
                    Toast.makeText(requireContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show()
                }
                PasswordChange.WRONG_CURRENT -> passwordDialog?.showWrongCurrentPassword()
                PasswordChange.FAILED -> {
                    passwordDialog?.setBusy(false)
                    Toast.makeText(
                        requireContext(),
                        "Không đổi được mật khẩu. Vui lòng kiểm tra mạng và thử lại!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        btnLogout.setOnClickListener {
            NotificationCenter.signOut(requireContext())
            authViewModel.logout()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }

    override fun onDestroyView() {
        // A dialog must not outlive the screen it belongs to
        passwordDialog?.dismiss()
        passwordDialog = null
        super.onDestroyView()
    }

    // Lists what the admin recorded about a student or a teacher; an admin account has no such details
    private fun showRoleInfo(header: TextView, container: ViewGroup, user: User) {
        val (title, lines) = when (user.role) {
            Role.STUDENT -> "Thông tin học tập" to listOf(
                "Mã sinh viên" to user.studentCode,
                "Lớp" to user.className,
                "Khoa / Ngành học" to user.major
            )
            Role.TEACHER -> "Thông tin công tác" to listOf(
                "Chuyên môn" to user.subject,
                "Khoa / Bộ môn" to user.department,
                "Học vị" to user.degree
            )
            else -> "" to emptyList()
        }
        header.text = title
        header.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        container.visibility = header.visibility

        container.removeAllViews()
        for ((label, value) in lines) {
            val line = layoutInflater.inflate(R.layout.item_profile_info, container, false)
            line.findViewById<TextView>(R.id.tvProfileInfoLabel).text = label
            line.findViewById<TextView>(R.id.tvProfileInfoValue).apply {
                if (value.isBlank()) {
                    text = "Chưa cập nhật"
                    setTextAppearance(R.style.TextAppearance_Kstudy_Body_Secondary)
                } else {
                    text = value
                }
            }
            container.addView(line)
        }
    }
}
