package app.blinkers

import android.app.Application
import timber.log.Timber

class BlinkersApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}