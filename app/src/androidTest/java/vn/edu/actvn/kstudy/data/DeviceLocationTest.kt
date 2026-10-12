package vn.edu.actvn.kstudy.data

import android.Manifest
import android.content.Context
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.actvn.kstudy.FragmentHostActivity
import vn.edu.actvn.kstudy.data.location.DeviceLocation

// Asks a real device (or the emulator) for its position, the way the app does when a session
// is opened or a code is scanned.
@RunWith(AndroidJUnit4::class)
class DeviceLocationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun current_withPermissionAndLocationSwitchedOn_returnsARealPosition() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        assumeTrue("location is switched off on this device", LocationManagerCompat.isLocationEnabled(manager))
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(DeviceLocation.hasPermission(context))

        // Android only gives a position to an app that is on screen, as the app is when it asks
        ActivityScenario.launch(FragmentHostActivity::class.java).use {
            // On the main thread, like the ViewModels that call it
            val position = runBlocking { withContext(Dispatchers.Main) { DeviceLocation.current(context) } }

            assertNotNull("no position arrived in time", position)
            assertTrue(position!!.location.latitude in -90.0..90.0)
            assertTrue(position.location.longitude in -180.0..180.0)
            assertFalse("the emulator's own GPS must not be taken for a fake GPS app", position.isMock)
        }
    }
}
