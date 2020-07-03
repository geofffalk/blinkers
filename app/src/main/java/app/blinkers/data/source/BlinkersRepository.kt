package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.*

interface BlinkersRepository {

    fun observeBlinkersStatus(): LiveData<BlinkersStatus>

    suspend fun setLedState(isOn: Boolean)

    suspend fun saveEmotionSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>>

    suspend fun getEmotionalStatesFrom(timestamp: Long): Result<List<EmotionalSnapshot>>

    suspend fun getAnalysisFrom(timestamp: Long): Result<List<Analysis>>
}