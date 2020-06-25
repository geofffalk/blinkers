package app.blinkers

import android.util.Log
import androidx.lifecycle.*
import app.blinkers.data.*
import app.blinkers.data.BrainWaves
import app.blinkers.data.source.BlinkersRepository
import kotlinx.coroutines.launch
import java.lang.Exception

class ControllerViewModel(
    private val blinkersRepository: BlinkersRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    init {
        Log.d("SERV", "Started")
    }

    private var connected = false
    private var notificationMsg: String? = null

    private val _forceUpdate = MutableLiveData<Boolean>(false)

    private val _ledOneStatusLabel = MutableLiveData<Int>()
    val ledOneStatusLabel: LiveData<Int> = _ledOneStatusLabel

    private val _dataLoading = MutableLiveData<Boolean>()
    val dataLoading: LiveData<Boolean> = _dataLoading


    var observeBrainWaves: LiveData<Result<BrainWaves>> = blinkersRepository.observeLatestBlinkersState().map {
        return@map when (it) {
            is Result.Success -> {
                Result.Success(it.data.brainWaves)
            }
            is Result.Error -> {
                Result.Error(it.exception)
            }
            else -> Result.Error(Exception("Unknown"))
        }
    }
    var observeLed: LiveData<Result<Int>> = blinkersRepository.observeLatestBlinkersState().map {
        return@map when (it) {
            is Result.Success -> {
                Result.Success(it.data.ledStatus)
            }
            is Result.Error -> {
                Result.Error(it.exception)
            }
            else -> Result.Error(Exception("Unknown"))
        }
    }

    private val _snackbarText = MutableLiveData<Event<Int>>()
    val snackbarText: LiveData<Event<Int>> = _snackbarText

    private val _serialConnectEvent = MutableLiveData<Boolean>()
    val serialConnectEvent: LiveData<Boolean> = _serialConnectEvent

    private val _serialConnectErrorEvent = MutableLiveData<Event<String>>()
    val serialConnectErrorEvent: LiveData<Event<String>> = _serialConnectErrorEvent

    private val _serialReadEvent = MutableLiveData<Event<ByteArray>>()
    val serialReadEvent: LiveData<Event<ByteArray>> = _serialReadEvent

    private val _serialIOErrorEvent = MutableLiveData<Event<String>>()
    val serialIOErrorEvent: LiveData<Event<String>> = _serialIOErrorEvent

    private val isDataLoadingError = MutableLiveData<Boolean>()

    init {
        _ledOneStatusLabel.value =  R.string.off
        loadLedStatus(true)
    }

    fun loadLedStatus(forceUpdate: Boolean) {
        _forceUpdate.value = forceUpdate
    }

    fun switchLed(isOn: Boolean) = viewModelScope.launch {
        blinkersRepository.setLedState(isOn)
    }

    private fun updateLedStatus(ledResult: Result<List<Led>>): LiveData<List<Led>> {
        val result = MutableLiveData<List<Led>>()

        if (ledResult is Result.Success) {
            isDataLoadingError.value = false
            viewModelScope.launch {
                result.value = ledResult.data
            }
        } else {
            result.value = emptyList()
            showSnackbarMessage(R.string.communication_error)
            isDataLoadingError.value = true
        }

        return result;
    }


    private fun showSnackbarMessage(message: Int) {
        _snackbarText.value = Event(message)
    }
}