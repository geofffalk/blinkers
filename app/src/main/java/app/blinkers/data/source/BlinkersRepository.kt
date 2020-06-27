package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BlinkersStatus
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result

interface BlinkersRepository {

    fun observeBlinkersStatus(): LiveData<BlinkersStatus>

    fun recordDeviceState(doRecord: Boolean)

    suspend fun setLedState(isOn: Boolean)

    suspend fun saveEmotionSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getDeviceStatesFrom(timestamp: Long)

    suspend fun getEmotionalStatesFrom(timestamp: Long)
}