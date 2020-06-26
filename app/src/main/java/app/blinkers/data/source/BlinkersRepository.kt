package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result

interface BlinkersRepository {

    fun observeLatestDeviceState(): LiveData<Result<DeviceState>>

    fun observeConnectionStatus(): LiveData<Result<String>>

    suspend fun setLedState(isOn: Boolean)

    suspend fun saveEmotionSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getDeviceStatesFrom(timestamp: Long)

    suspend fun getEmotionalStatesFrom(timestamp: Long)
}