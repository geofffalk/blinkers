package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import app.blinkers.data.BlinkersState
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
    override fun observeLastBlinkerState(): LiveData<Result<BlinkersState>> {
        return blinkerDao.observeLastBlinkerState().map {
            Success(it)
        }
    }

    override suspend fun saveBlinkerState(blinkersState: BlinkersState) = withContext(ioDispatcher) {
        blinkerDao.insertBlinkerState(blinkersState)
    }

    override suspend fun getBlinkerStates(): Result<List<BlinkersState>> = withContext(ioDispatcher) {
        return@withContext try {
            Success(blinkerDao.getBlinkerStates())
        } catch (e: Exception) {
            Error(e)
        }
    }

    override suspend fun deleteAllStates() = withContext(ioDispatcher) {
        blinkerDao.deleteBlinkerStates()
    }
}