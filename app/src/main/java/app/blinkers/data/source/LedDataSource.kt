package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.Led
import app.blinkers.data.LedStatus
import app.blinkers.data.Result

interface LedDataSource {

    fun observeLed(): LiveData<Result<LedStatus>>

    suspend fun updateLed(led: Led, isOn: Boolean)
}