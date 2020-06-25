package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import app.blinkers.data.BlinkersState
import app.blinkers.data.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DefaultBlinkersRepository(
    private val blinkersLiveDataSource: BlinkersDataSource,
    private val blinkersLocalDataSource: BlinkersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {


        init {
            val blinkerObserver = Observer<Result<BlinkersState>> { blinkerState ->
                blinkerState?.let { }
            }

                blinkersLiveDataSource.observeLastBlinkerState().observeForever(blinkerObserver)
            }


    override fun observeLatestBlinkersState(): LiveData<Result<BlinkersState>> = blinkersLiveDataSource.observeLastBlinkerState().map {
        if (it is Result.Success) {
            GlobalScope.launch(ioDispatcher) {
                blinkersLocalDataSource.saveBlinkerState(it.data)
            }
        }
        it
    }

    override suspend fun setLedState(isOn: Boolean) {
        (blinkersLiveDataSource as BlinkersLiveDataSource).updateLed(isOn)
    }

}