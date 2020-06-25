package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BlinkersState
import app.blinkers.data.Result

interface BlinkersDataSource {

    fun observeLastBlinkerState() : LiveData<Result<BlinkersState>>

    suspend fun saveBlinkerState(blinkersState: BlinkersState)

    suspend fun getBlinkerStates(): Result<List<BlinkersState>>

    suspend fun deleteAllStates()
}