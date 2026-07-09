package vn.edu.actvn.kstudy.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.data.model.Role
import vn.edu.actvn.kstudy.ui.admin.AdminMainActivity
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

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)



        btnLogin.setOnClickListener {
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            viewModel.login(user, pass)
        }

        // Observe results
        viewModel.loginResult.observe(this) { (success, message) ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
