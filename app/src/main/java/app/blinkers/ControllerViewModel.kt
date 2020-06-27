package app.blinkers

import androidx.lifecycle.*
import app.blinkers.data.*
import app.blinkers.data.source.BlinkersRepository
import kotlinx.coroutines.launch
import java.lang.Exception

class ControllerViewModel(
    private val blinkersRepository: BlinkersRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var connected = false
    private var notificationMsg: String? = null

    val dominance = MutableLiveData<Int>()
    val valence = MutableLiveData<Int>()
    val arousal = MutableLiveData<Int>()

    val statusText: LiveData<String> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.toString()
    }

   val isRecording: LiveData<Boolean> = blinkersRepository.observeBlinkersStatus().map {
       it.isRecording
   }

    var observeBrainWaves: LiveData<Result<EEGSnapshot>> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.latestEEGSnapshot?.let {
            Result.Success(it)
        }
        Result.Error(Exception("No EEG results"))
    }

    val ledIsOn: LiveData<Boolean> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.isLedOn
    }

    private val _snackbarText = MutableLiveData<Event<Int>>()
    val snackbarText: LiveData<Event<Int>> = _snackbarText


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

    fun recordDeviceState(doRecord: Boolean) {
            blinkersRepository.recordDeviceState(doRecord)
    }
}