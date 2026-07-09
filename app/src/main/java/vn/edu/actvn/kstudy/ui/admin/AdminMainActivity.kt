package vn.edu.actvn.kstudy.ui.admin

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.lifecycle.ViewModelProvider
import vn.edu.actvn.kstudy.R
import vn.edu.actvn.kstudy.ui.common.ProfileFragment
import vn.edu.actvn.kstudy.viewmodel.AuthViewModel

class AdminMainActivity : AppCompatActivity() {

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_admin_main)

        val userId = intent.getStringExtra("USER_ID") ?: ""
        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        authViewModel.loadUser(userId)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        val mainRoot = findViewById<RelativeLayout>(R.id.adminMainRoot)
        val fragmentContainer = findViewById<FrameLayout>(R.id.adminFragmentContainer)
        val bottomNav = findViewById<BottomNavigationView>(R.id.adminBottomNav)
        bottomNav.itemActiveIndicatorColor = androidx.core.content.ContextCompat.getColorStateList(this, R.color.nav_indicator)

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_admin_dashboard -> AdminDashboardFragment()
                R.id.menu_admin_teachers -> ManageTeachersFragment()
                R.id.menu_admin_students -> ManageStudentsFragment()
                R.id.menu_admin_classes -> ManageClassesFragment()
                R.id.menu_admin_profile -> ProfileFragment()
                else -> AdminDashboardFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .commit()
            true
        }

        // Set default fragment
        if (saved == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.adminFragmentContainer, AdminDashboardFragment())
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
