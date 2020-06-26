package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DefaultBlinkersRepository(
    private val deviceCommunicator: DeviceCommunicator,
    private val localDataSource: BlinkersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {


        init {
            val blinkerObserver = Observer<Result<DeviceState>> { blinkerState ->
                blinkerState?.let { }
            }

            deviceCommunicator.observeLatestDeviceState().observeForever(blinkerObserver)
            }


    override fun observeLatestDeviceState(): LiveData<Result<DeviceState>> = deviceCommunicator.observeLatestDeviceState().map {
        if (it is Result.Success) {
            GlobalScope.launch(ioDispatcher) {
                localDataSource.saveDeviceState(it.data)
            }
        }
        it
    }

    override fun observeConnectionStatus(): LiveData<Result<String>> = deviceCommunicator.observeConnectionStatus()

    override suspend fun setLedState(isOn: Boolean) {
        deviceCommunicator.updateLed(isOn)
    }

    override suspend fun saveEmotionSnapshot(snapshot: EmotionalSnapshot) {
        localDataSource.saveEmotionalSnapshot(snapshot)
    }

    override suspend fun getDeviceStatesFrom(timestamp: Long) {
        localDataSource.getDeviceStatesFrom(timestamp)
    }

    override suspend fun getEmotionalStatesFrom(timestamp: Long) {
        localDataSource.getEmotionalSnapshotsFrom(timestamp)
    }

}