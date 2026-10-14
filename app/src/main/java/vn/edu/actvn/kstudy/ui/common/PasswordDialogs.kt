package vn.edu.actvn.kstudy.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.util.PasswordRules

// The dialog in which a user changes their own password. It stays open until the password has
// really been changed, so that a wrong current password can be marked at its field.
//
// signedInPassword: given right after signing in with a password an admin chose. The dialog then
// asks only for the new password, cannot be dismissed, and offers to sign out (onSignOut) instead.
class ChangePasswordDialog(
    context: Context,
    private val signedInPassword: String? = null,
    onSignOut: () -> Unit = {},
    private val onSubmit: (currentPassword: String, newPassword: String) -> Unit
) {
    private val content = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
    private val tilCurrent = content.findViewById<TextInputLayout>(R.id.tilCurrentPassword)
    private val tilNew = content.findViewById<TextInputLayout>(R.id.tilNewPassword)
    private val tilConfirm = content.findViewById<TextInputLayout>(R.id.tilConfirmPassword)
    private val etCurrent = content.findViewById<EditText>(R.id.etCurrentPassword)
    private val etNew = content.findViewById<EditText>(R.id.etNewPassword)
    private val etConfirm = content.findViewById<EditText>(R.id.etConfirmPassword)

    private val dialog: AlertDialog = if (signedInPassword == null) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Đổi mật khẩu")
            .setView(content)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Đổi mật khẩu", null)
            .create()
    } else {
        tilCurrent.visibility = View.GONE
        MaterialAlertDialogBuilder(context)
            .setTitle("Đặt mật khẩu của bạn")
            .setMessage(
                "Mật khẩu bạn vừa dùng do quản trị viên cấp. Hãy đặt mật khẩu của riêng bạn để tiếp tục."
            )
            .setView(content)
            .setCancelable(false)
            .setNegativeButton("Đăng xuất") { _, _ -> onSignOut() }
            .setPositiveButton("Lưu mật khẩu", null)
            .create()
    }

    fun show() {
        dialog.show()
        FormFields.clearErrorOnEdit(tilCurrent, tilNew, tilConfirm)
        // Set after show() so that a problem keeps the dialog open instead of closing it
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
    }

    // The answer of the server: the current password was not the right one
    fun showWrongCurrentPassword() {
        setBusy(false)
        FormFields.setError(tilCurrent, PasswordRules.WRONG_CURRENT)
    }

    // No second request while the first one is on its way
    fun setBusy(busy: Boolean) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
    }

    fun dismiss() {
        dialog.dismiss()
    }

    private fun submit() {
        // The sign-in form trims the password as well
        val current = signedInPassword ?: etCurrent.text.toString().trim()
        val new = etNew.text.toString()

        // Every check runs, so that every problem is marked at once
        val currentFilled = signedInPassword != null || FormFields.requireFilled(tilCurrent)
        val newProblem = PasswordRules.problemWithNew(new, current)
        val confirmProblem = PasswordRules.problemWithConfirmation(new, etConfirm.text.toString())
        FormFields.setError(tilNew, newProblem)
        FormFields.setError(tilConfirm, confirmProblem)
        if (!currentFilled || newProblem != null || confirmProblem != null) return

        setBusy(true)
        onSubmit(current, new)
    }
}

// The dialog in which an admin gives somebody else a new password
object ResetPasswordDialog {

    fun show(context: Context, personName: String, onConfirm: (newPassword: String) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.dialog_reset_password, null)
        val tilPassword = content.findViewById<TextInputLayout>(R.id.tilResetPassword)
        val etPassword = content.findViewById<EditText>(R.id.etResetPassword)
        // A random suggestion, so that the admin does not fall back on something easy to guess
        etPassword.setText(PasswordRules.generate())

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Đặt lại mật khẩu")
            .setMessage(
                "Tài khoản của $personName sẽ đăng nhập bằng mật khẩu mới này, " +
                    "rồi phải đặt mật khẩu của riêng mình."
            )
            .setView(content)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Đặt lại", null)
            .show()
        FormFields.clearErrorOnEdit(tilPassword)
        // Set after show() so that a problem keeps the dialog open instead of closing it
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val password = etPassword.text.toString()
            val problem = PasswordRules.problemWithNew(password)
            FormFields.setError(tilPassword, problem)
            if (problem == null) {
                onConfirm(password)
                dialog.dismiss()
            }
        }
    }
}
