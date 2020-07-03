package app.blinkers.data.source

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import app.blinkers.data.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.sqrt

class DefaultBlinkersRepository(
    private val deviceCommunicator: DeviceCommunicator,
    private val localDataSource: BlinkersDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {

    private val maxSizeOfCache = 5
    private val lifetimeOfEEGSnapshotsInMillis = 3000L
    private val eegRatioPrecision = 1000000
    private val eegSnapshotCache = object: LinkedHashMap<Long, EEGSnapshot>() {
        override fun removeEldestEntry(eldest: Map.Entry<Long, EEGSnapshot>?): Boolean {
            return size >= maxSizeOfCache
        }
    }

        init {
            val blinkerObserver = Observer<Result<DeviceState>> { blinkerState ->
                blinkerState?.let {
                    if (it is Result.Success && it.data.eegSnapshot != null) {
                        eegSnapshotCache[it.data.timestamp] = it.data.eegSnapshot
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


        return BlinkersStatus(isConnected, isLedOn, latestEmotionalSnapshot, latestEEGSnapshot, errorMessage)
    }

    override suspend fun setLedState(isOn: Boolean) {
        deviceCommunicator.updateLed(isOn)
    }

    override suspend fun saveEmotionSnapshot(emo: EmotionalSnapshot) {
        val currentTime = System.currentTimeMillis()
        val analysis = eegSnapshotCache.filter { currentTime - it.key < lifetimeOfEEGSnapshotsInMillis  }
            .map { Analysis(it.key, emo.valence, emo.arousal, emo.dominance, it.value.normalised(eegRatioPrecision)) }
        analysis.forEach {
            localDataSource.saveAnalysis(it)
        }
        localDataSource.saveEmotionalSnapshot(emo)
    }

    override suspend fun getDeviceStatesFrom(timestamp: Long): Result<List<DeviceState>> {
        return localDataSource.getDeviceStatesFrom(timestamp)
    }

    override suspend fun getEmotionalStatesFrom(timestamp: Long): Result<List<EmotionalSnapshot>> {
        return localDataSource.getEmotionalSnapshotsFrom(timestamp)
    }

    override suspend fun getAnalysisFrom(timestamp: Long): Result<List<Analysis>> {
        return localDataSource.getAnalysisFrom(timestamp)
    }

}
