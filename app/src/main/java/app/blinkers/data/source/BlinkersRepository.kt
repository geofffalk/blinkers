package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.blinkers.data.*

interface BlinkersRepository {

    fun observeBlinkersStatus(): LiveData<BlinkersStatus>

    suspend fun saveEmotionSnapshot(emotionalSnapshot: EmotionalSnapshot)

    suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>>

    suspend fun getEmotionalStatesFrom(timestamp: Long): Result<List<EmotionalSnapshot>>

    suspend fun getAnalysisFrom(timestamp: Long): Result<List<Analysis>>

    suspend fun setPhaseTime(phase: Int, seconds: Int)

    suspend fun setSpeed(speed: Int)

    fun startProgram(phase0Seconds: Short, phase1Seconds: Short, phase2Seconds: Short, phase3Seconds: Short, repeatMinutes: Short)
}