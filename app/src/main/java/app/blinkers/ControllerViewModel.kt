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

    val statusText: LiveData<String> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.toString()
    }

    val ledIsOn: LiveData<Boolean> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.isLedOn
    }

    fun switchLed(isOn: Boolean) = viewModelScope.launch {
        blinkersRepository.setLedState(isOn)
    }

    fun saveEmotionalSnapshot() {
        viewModelScope.launch {
            blinkersRepository.saveEmotionSnapshot(
                EmotionalSnapshot(
                    timestamp = System.currentTimeMillis(),
                    arousal = arousal.value ?: -1,
                    dominance = dominance.value ?: -1,
                    valence = valence.value ?: -1
                ))
        }
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