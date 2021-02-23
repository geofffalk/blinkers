package app.blinkers

import android.widget.SeekBar
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.databinding.PropertyChangeRegistry
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
    val sessionTime = MutableLiveData<Int>()
    val brightness = MutableLiveData<Int>()
    val endStage =  MutableLiveData<Int>()

    init {
        startStage.postValue(0)
        endStage.postValue(7)
        sessionTime.postValue(5)
        brightness.postValue(8)
        colorScheme.postValue(0)
    }

    val statusText: LiveData<String> = blinkersRepository.observeBlinkersStatus().map { blinkersStatus ->
        blinkersStatus.toString()
    }

    fun startProgram() {
     //   blinkersRepository.setBrightness(brightness.value ?: 8)
        val start = (startStage.value ?: 0)
        val end = (endStage.value ?: 7)
        val sessionTime = sessionTime.value ?: 10

        blinkersRepository.startProgram(start, end, sessionTime, colorScheme.value ?: 0, brightness.value ?: 8)
    }

    fun onBrightnessChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        brightness.value = progress
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

open class ObservableViewModel : ViewModel(), Observable {

    private val callbacks: PropertyChangeRegistry = PropertyChangeRegistry()

    override fun addOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        callbacks.add(callback)
    }

    override fun removeOnPropertyChangedCallback(callback: Observable.OnPropertyChangedCallback) {
        callbacks.remove(callback)
    }

    /**
     * Notifies listeners that all properties of this instance have changed.
     */
    fun notifyChange() {
        callbacks.notifyCallbacks(this, 0, null)
    }

    /**
     * Notifies listeners that a specific property has changed. The getter for the property
     * that changes should be marked with [Bindable] to generate a field in
     * `BR` to be used as `fieldId`.
     *
     * @param fieldId The generated BR id for the Bindable field.
     */
    fun notifyPropertyChanged(fieldId: Int) {
        callbacks.notifyCallbacks(this, fieldId, null)
    }
}