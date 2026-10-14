package vn.edu.actvn.kstudy.ui.teacher

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import vn.edu.actvn.kstudy.R
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.data.notification.NotificationCenter
import vn.edu.actvn.kstudy.data.notification.NotificationPermission
import vn.edu.actvn.kstudy.ui.common.AiAssistantFragment
import vn.edu.actvn.kstudy.ui.common.ProfileFragment
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class TeacherMainActivity : AppCompatActivity() {

    var teacherUserId: String = ""

    // Without the permission the bell in the app still works. Once it is given, what arrived
    // while the question was open is shown in the status bar.
    private val askForNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) NotificationCenter.onNotificationsAllowed(this)
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_teacher_main)

        teacherUserId = intent.getStringExtra("USER_ID") ?: ""
        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        authViewModel.loadUser(teacherUserId)

        NotificationCenter.start(this, teacherUserId)
        NotificationPermission.requestIfNeeded(this, askForNotifications)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val mainRoot = findViewById<RelativeLayout>(R.id.teacherMainRoot)
        val fragmentContainer = findViewById<FrameLayout>(R.id.teacherFragmentContainer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.teacherBottomNav)
        bottomNav.itemActiveIndicatorColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_indicator)

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_teacher_dashboard -> TeacherDashboardFragment.newInstance(teacherUserId)
                R.id.menu_teacher_classes -> TeacherClassesFragment.newInstance(teacherUserId)
                R.id.menu_teacher_ai -> AiAssistantFragment.newInstance()
                R.id.menu_teacher_profile -> ProfileFragment()
                else -> TeacherDashboardFragment.newInstance(teacherUserId)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.teacherFragmentContainer, fragment)
                .commit()
            true
        }

        if (saved == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.teacherFragmentContainer, TeacherDashboardFragment.newInstance(teacherUserId))
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
