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
import vn.edu.actvn.kstudy.ui.LoginActivity
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class ProfileFragment : Fragment() {

    private lateinit var authViewModel: AuthViewModel

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
        val etName = view.findViewById<EditText>(R.id.etProfileName)
        val etPhone = view.findViewById<EditText>(R.id.etProfilePhone)
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

        btnUpdate.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            authViewModel.updateProfile(name, phone)
        }

        btnLogout.setOnClickListener {
            authViewModel.logout()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        }

        return view
    }
}
