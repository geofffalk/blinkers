package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result

interface BlinkersDataSource {

    fun observeLastBlinkerState() : LiveData<Result<DeviceState>>

    suspend fun saveDeviceState(deviceState: DeviceState)

    suspend fun getDeviceStates(): Result<List<DeviceState>>

    suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>>

    suspend fun getLastDeviceState(): Result<DeviceState>

    suspend fun saveEmotionalSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getLastEmotionalSnapshot(): Result<EmotionalSnapshot>

    suspend fun getEmotionalSnapshotsFrom(timestamp: Long): Result<List<EmotionalSnapshot>>
}