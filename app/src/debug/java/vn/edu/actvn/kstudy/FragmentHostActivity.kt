package vn.edu.actvn.kstudy

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

// Hosts a single fragment for the UI tests in src/androidTest. The real main activities cannot be
// used there because they load the signed-in user from Firestore as soon as they start.
class FragmentHostActivity : AppCompatActivity() {

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)

        val container = FrameLayout(this).apply {
            id = CONTAINER_ID
            setBackgroundColor(getColor(R.color.background))
        }
        setContentView(container)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // Same layout as the real main activities: content below the status bar and above the system navigation
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
    }

    fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(CONTAINER_ID, fragment)
            .commitNow()
    }

    companion object {
        val CONTAINER_ID: Int = View.generateViewId()
    }
}
