package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result
import app.blinkers.data.Result.Error
import app.blinkers.data.Result.Success
import app.blinkers.data.source.local.BlinkerDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception

class BlinkersLocalDataSource internal constructor(
    private val blinkerDao: BlinkerDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): BlinkersDataSource {
    override fun observeLastBlinkerState(): LiveData<Result<DeviceState>> = blinkerDao.observeLatestDeviceState().map {
            Success(it)
    }

    override suspend fun saveDeviceState(deviceState: DeviceState) = withContext(ioDispatcher) {
        blinkerDao.insertDeviceState(deviceState)
    }

    override suspend fun getDeviceStates(): Result<List<DeviceState>> = withContext(ioDispatcher) {
        return@withContext try {
            Success(blinkerDao.getDeviceStates())
        } catch (e: Exception) {
            Error(e)
        }
    }

    override suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>> = withContext(ioDispatcher) {
        return@withContext try {
            Success(blinkerDao.getDeviceStatesFrom(timestamp))
        } catch (e: Exception) {
            Error(e)
        }
    }

    override suspend fun saveEmotionalSnapshot(emotionalSnapshot: EmotionalSnapshot) = withContext(ioDispatcher) {
        blinkerDao.insertEmotionalSnapshot(emotionalSnapshot)
    }

    override suspend fun getEmotionalSnapshotsFrom(timestamp: Long): Result<List<EmotionalSnapshot>> = withContext(ioDispatcher) {
        return@withContext try {
            Success(blinkerDao.getEmotionalSnapshotsFrom(timestamp))
        } catch (e: Exception) {
            Error(e)
        }
    }
}