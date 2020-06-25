package app.blinkers

import android.app.Application
import app.blinkers.data.ServiceLocator
import app.blinkers.data.source.BlinkersRepository
import timber.log.Timber

class BlinkersApp : Application() {


    // Depends on the flavor,
    val blinkersRepository: BlinkersRepository
        get() = ServiceLocator.provideBlinkersRepository(this)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}