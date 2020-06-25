package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BlinkersState
import app.blinkers.data.Result

interface BlinkersRepository {

    fun observeLatestBlinkersState(): LiveData<Result<BlinkersState>>

    suspend fun setLedState(isOn: Boolean)
}