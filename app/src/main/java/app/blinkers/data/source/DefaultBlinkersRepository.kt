package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.map
import app.blinkers.data.BlinkersStatus
import app.blinkers.data.DeviceState
import app.blinkers.data.EmotionalSnapshot
import app.blinkers.data.Result
import kotlinx.coroutines.*

class DefaultBlinkersRepository(
    private val deviceCommunicator: DeviceCommunicator,
    private val localDataSource: BlinkersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {

    private var isRecordingDeviceState = false

        init {
            val blinkerObserver = Observer<Result<DeviceState>> { blinkerState ->
                blinkerState?.let {
                    if (isRecordingDeviceState && it is Result.Success) {
                        GlobalScope.launch(ioDispatcher) {
                            localDataSource.saveDeviceState(it.data)
                        }
                    }
                }
            }

            deviceCommunicator.observeLatestDeviceState().observeForever(blinkerObserver)
            }


    override fun observeBlinkersStatus(): LiveData<BlinkersStatus> = Transformations.map(deviceCommunicator.observeLatestDeviceState()) {
        var latestEmotionalSnapshot: EmotionalSnapshot? = null;
        GlobalScope.launch {
            val result = localDataSource.getLastEmotionalSnapshot()
            (result as? Result.Success)?.apply {
                latestEmotionalSnapshot = this.data
            }
        }
        if (it is Result.Success) {
            BlinkersStatus(true, isRecordingDeviceState, it.data.ledStatus == 1, latestEmotionalSnapshot, it.data.eegSnapshot)
        }
        BlinkersStatus(false, isRecordingDeviceState, false, latestEmotionalSnapshot, null, (it as? Result.Error)?.exception?.message)
    }

    override fun recordDeviceState(doRecord: Boolean) {
        isRecordingDeviceState = doRecord
    }

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