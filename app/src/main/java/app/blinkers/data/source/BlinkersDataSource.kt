package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.Analysis
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result

interface BlinkersDataSource {

    fun observeLastDeviceState() : LiveData<Result<DeviceState>>

    fun observeLastEmotionalSnapshot() : LiveData<Result<EmotionalSnapshot>>

    suspend fun saveDeviceState(deviceState: DeviceState)

    suspend fun getDeviceStates(): Result<List<DeviceState>>

    suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>>

    suspend fun saveEmotionalSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getEmotionalSnapshotsFrom(timestamp: Long): Result<List<EmotionalSnapshot>>

    suspend fun saveAnalysis(analysis: Analysis)

    suspend fun getAnalysisFrom(timestamp: Long): Result<List<Analysis>>
}