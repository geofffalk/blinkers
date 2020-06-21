package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.Led
import app.blinkers.data.LedStatus
import app.blinkers.data.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class LedRepoImpl(
    private val ledDataSource: LedDataSource
) : LedRepository {

    override fun observeLed(): LiveData<Result<LedStatus>> = ledDataSource.observeLed()

    override suspend fun updateLed(led: Led, isOn: Boolean) = ledDataSource.updateLed(led, isOn)

}