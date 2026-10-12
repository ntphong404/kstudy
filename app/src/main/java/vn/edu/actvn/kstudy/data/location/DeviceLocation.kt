package vn.edu.actvn.kstudy.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import vn.edu.actvn.kstudy.data.model.GeoLocation
import vn.edu.actvn.kstudy.util.CheckInRules
import kotlin.coroutines.resume

// Reads the phone's position with the platform LocationManager, so the app needs no extra
// Google Play services library.
object DeviceLocation {

    // Both have to be requested together: since Android 12 the system ignores a request for
    // the precise permission alone.
    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private const val TIMEOUT_MILLIS = 8_000L

    // An older position may have been taken somewhere else
    private const val MAX_LAST_KNOWN_AGE_MILLIS = 60_000L

    // An approximate position is off by up to a few kilometres, useless for a 100 m check
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // Null when the permission is missing, location is switched off or no position arrives in time
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): CheckInRules.Position? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        if (providers.isEmpty()) return null

        val location = withTimeoutOrNull(TIMEOUT_MILLIS) { firstFix(context, manager, providers) }
            ?: lastKnown(manager, providers)
            ?: return null
        return CheckInRules.Position(
            location = GeoLocation(location.latitude, location.longitude),
            isMock = LocationCompat.isMock(location),
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f
        )
    }

    // Asks every provider at once and takes the first answer: GPS is exact but slow indoors,
    // the network provider is quick. Callbacks arrive on the main thread, one at a time.
    @SuppressLint("MissingPermission")
    private suspend fun firstFix(
        context: Context,
        manager: LocationManager,
        providers: List<String>
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        var pending = providers.size
        for (provider in providers) {
            LocationManagerCompat.getCurrentLocation(
                manager, provider, signal, ContextCompat.getMainExecutor(context)
            ) { location ->
                pending--
                val fix: Location? = location
                if (continuation.isActive) {
                    if (fix != null) {
                        continuation.resume(fix)
                        signal.cancel()
                    } else if (pending == 0) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { ageMillis(it) <= MAX_LAST_KNOWN_AGE_MILLIS }
            .minByOrNull { ageMillis(it) }

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
}
