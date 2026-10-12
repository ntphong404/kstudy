package vn.edu.actvn.kstudy.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputLayout
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.ui.admin.AdminMainActivity
import vn.edu.actvn.kstudy.ui.common.FormFields
import vn.edu.actvn.kstudy.ui.student.StudentMainActivity
import vn.edu.actvn.kstudy.ui.teacher.TeacherMainActivity
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        viewModel.initializeMockData()

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val tvError = findViewById<TextView>(R.id.tvLoginError)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        FormFields.clearErrorOnEdit(tilUsername, tilPassword)

        // The button is locked while the account is being checked, so it cannot be sent twice
        fun setSigningIn(signingIn: Boolean) {
            btnLogin.isEnabled = !signingIn
            btnLogin.text = if (signingIn) "Đang đăng nhập…" else "Đăng nhập"
        }

        btnLogin.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            tvError.visibility = View.GONE
            FormFields.setError(tilUsername, if (user.isEmpty()) "Vui lòng nhập tên đăng nhập" else null)
            FormFields.setError(tilPassword, if (pass.isEmpty()) "Vui lòng nhập mật khẩu" else null)
            if (user.isEmpty() || pass.isEmpty()) return@setOnClickListener

            setSigningIn(true)
            viewModel.login(user, pass)
        }

        // Observe results
        viewModel.loginResult.observe(this) { (success, message) ->
            setSigningIn(false)
            if (success) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                tvError.text = message
                tvError.visibility = View.VISIBLE
            }
        }

        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                val intent = when (user.role) {
                    Role.ADMIN -> Intent(this, AdminMainActivity::class.java)
                    Role.TEACHER -> Intent(this, TeacherMainActivity::class.java)
                    Role.STUDENT -> Intent(this, StudentMainActivity::class.java)
                }
                // Store user context in intent or global singleton (Intent extras here)
                intent.putExtra("USER_ID", user.id)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
