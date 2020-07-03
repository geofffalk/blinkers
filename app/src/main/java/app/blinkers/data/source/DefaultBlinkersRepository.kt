package app.blinkers.data.source

import androidx.lifecycle.*
import app.blinkers.data.*
import kotlinx.coroutines.*
import timber.log.Timber

class DefaultBlinkersRepository(
    private val deviceCommunicator: DeviceCommunicator,
    private val localDataSource: BlinkersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {

    private var isRecordingDeviceState = false
    private var lastEmotionalSnapshot : EmotionalSnapshot? = null;

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


    override fun observeBlinkersStatus(): LiveData<BlinkersStatus>  {
        val deviceLiveData = deviceCommunicator.observeLatestDeviceState()
        val emotionLiveData = localDataSource.observeLastEmotionalSnapshot()

        val result = MediatorLiveData<BlinkersStatus>()

        result.addSource(deviceLiveData) {
            result.value = combineDataSources(deviceLiveData, emotionLiveData)
        }

        result.addSource(emotionLiveData) {
            result.value = combineDataSources(deviceLiveData, emotionLiveData)
        }

        return result
    }

    private fun combineDataSources(
        deviceLiveData: LiveData<Result<DeviceState>>,
        emotionLiveData: LiveData<Result<EmotionalSnapshot>>
    ): BlinkersStatus? {
        var isConnected = false
        var isLedOn = false
        var latestEmotionalSnapshot: EmotionalSnapshot? = null
        var latestEEGSnapshot: EEGSnapshot? = null
        var errorMessage: String? = null

        (deviceLiveData.value as? Result.Success)?.apply {
            isConnected = true
            isLedOn = this.data.ledStatus == 1
            latestEEGSnapshot = this.data.eegSnapshot
        }

        (deviceLiveData.value as? Result.Error)?.apply {
            isConnected = false
            errorMessage = this.exception.message
        }

        (emotionLiveData.value as? Result.Success)?.data?.apply {
            latestEmotionalSnapshot = EmotionalSnapshot(this.timestamp, this.valence, this.arousal, this.dominance)
        }


        return BlinkersStatus(isConnected, isRecordingDeviceState, isLedOn, latestEmotionalSnapshot, latestEEGSnapshot, errorMessage)
    }

    override fun recordDeviceState(doRecord: Boolean) {
        isRecordingDeviceState = doRecord
    }

    override suspend fun setLedState(isOn: Boolean) {
        deviceCommunicator.updateLed(isOn)
    }

    override suspend fun saveEmotionSnapshot(snapshot: EmotionalSnapshot) {
        lastEmotionalSnapshot = snapshot;
        localDataSource.saveEmotionalSnapshot(snapshot)
    }

    override suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>> {
        return localDataSource.getDeviceStatesFrom(timestamp)
    }

    override suspend fun getEmotionalStatesFrom(timestamp: Long): Result<List<EmotionalSnapshot>> {
        return localDataSource.getEmotionalSnapshotsFrom(timestamp)
    }

}