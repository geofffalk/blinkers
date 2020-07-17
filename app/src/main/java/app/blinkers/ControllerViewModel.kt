package app.blinkers

import androidx.lifecycle.*
import app.blinkers.data.*
import app.blinkers.data.source.BlinkersRepository
import kotlinx.coroutines.launch

class ControllerViewModel(
    private val blinkersRepository: BlinkersRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val dominance = MutableLiveData<Int>()
    val valence = MutableLiveData<Int>()
    val arousal = MutableLiveData<Int>()
    val phase0Seconds = MutableLiveData<Int>()
    val phase1Seconds = MutableLiveData<Int>()
    val phase2Seconds = MutableLiveData<Int>()
    val phase3Seconds = MutableLiveData<Int>()
    val repeatMinutes = MutableLiveData<Int>()

    init {
        phase0Seconds.postValue(30)
        phase1Seconds.postValue(30)
        phase2Seconds.postValue(30)
        phase3Seconds.postValue(30)
        repeatMinutes.postValue(25)

        // TODO save settings in database when updated
    }

    val statusText: LiveData<String> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.toString()
    }

    fun startProgram() {
        // TODO move all this to repo
        blinkersRepository.startProgram(phase0Seconds.value!!, phase1Seconds.value!!, phase2Seconds.value!!, phase3Seconds.value!!, repeatMinutes.value!!)
    }

    fun startDestressProgram() {
        blinkersRepository.startProgram(30, 30, 0, 0, 0)
    }

    fun startChillaxProgram() {
        blinkersRepository.startProgram(0, 0, 30, 30, 0)
    }

    fun saveEmotionalSnapshot() {
        viewModelScope.launch {
            blinkersRepository.saveEmotionSnapshot(
                EmotionalSnapshot(
                    timestamp = System.currentTimeMillis(),
                    arousal = arousal.value ?: 0,
                    dominance = dominance.value ?: 0,
                    valence = valence.value ?: 0
                ))
        }
    }

    suspend fun getAnalysis(): List<Analysis>? {
        val result = blinkersRepository.getAnalysisFrom(0)
        return (result as? Result.Success)?.data
    }

    suspend fun getDeviceData(): List<DeviceState>? {
        val result = blinkersRepository.getDeviceStatesFrom(0)
        return (result as? Result.Success)?.data
    }

    suspend fun getEmotionData(): List<EmotionalSnapshot>? {
        val result = blinkersRepository.getEmotionalStatesFrom(0)
        return (result as? Result.Success)?.data
    }
}