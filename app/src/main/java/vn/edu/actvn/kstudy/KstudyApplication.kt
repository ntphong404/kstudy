package vn.edu.actvn.kstudy

import android.app.Application

class KstudyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install()
    }
}
