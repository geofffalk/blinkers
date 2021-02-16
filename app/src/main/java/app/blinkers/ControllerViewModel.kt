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
    val startStage = MutableLiveData<Int>()
    val colorScheme = MutableLiveData<Int>()
    val phaseTime = MutableLiveData<Int>()
    val brightness = MutableLiveData<Int>()

    init {
        startStage.postValue(1)
        phaseTime.postValue(1)
        brightness.postValue(8)
        colorScheme.postValue(1)
    }

    val statusText: LiveData<String> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.toString()
    }

    fun startProgram() {
     //   blinkersRepository.setBrightness(brightness.value ?: 8)
        blinkersRepository.startProgram(startStage.value ?: 1, 8, (phaseTime.value ?: 20) * 10, colorScheme.value ?: 1, brightness.value ?: 8)
    }

    fun stopProgram() {
        blinkersRepository.stopProgram()
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