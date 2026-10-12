package vn.edu.actvn.kstudy

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Firebase AI Logic only answers apps that pass App Check. A debug build proves itself with a
// debug token: find the "DebugAppCheckProvider" line in Logcat and register that token in
// Firebase Console > App Check > Apps > Manage debug tokens.
object AppCheckInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
