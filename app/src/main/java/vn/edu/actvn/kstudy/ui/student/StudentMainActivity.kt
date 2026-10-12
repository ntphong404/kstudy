package vn.edu.actvn.kstudy.ui.student

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import vn.edu.actvn.kstudy.R
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.ui.common.AiAssistantFragment
import vn.edu.actvn.kstudy.ui.common.ProfileFragment
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class StudentMainActivity : AppCompatActivity() {

    var studentUserId: String = ""

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_student_main)

        studentUserId = intent.getStringExtra("USER_ID") ?: ""
        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        authViewModel.loadUser(studentUserId)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val mainRoot = findViewById<RelativeLayout>(R.id.studentMainRoot)
        val fragmentContainer = findViewById<FrameLayout>(R.id.studentFragmentContainer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.studentBottomNav)
        bottomNav.itemActiveIndicatorColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_indicator)

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_student_dashboard -> StudentDashboardFragment.newInstance(studentUserId)
                R.id.menu_student_classes -> StudentClassesFragment.newInstance(studentUserId)
                R.id.menu_student_scan -> StudentScanFragment.newInstance(studentUserId)
                R.id.menu_student_ai -> AiAssistantFragment.newInstance()
                R.id.menu_student_profile -> ProfileFragment()
                else -> StudentDashboardFragment.newInstance(studentUserId)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.studentFragmentContainer, fragment)
                .commit()
            true
        }

        if (saved == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.studentFragmentContainer, StudentDashboardFragment.newInstance(studentUserId))
                .commit()
        }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val outRect = android.graphics.Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
