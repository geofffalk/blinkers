package app.blinkers

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.*
import app.blinkers.data.*
import app.blinkers.data.BrainWaves
import app.blinkers.data.source.BrainWavesRepository
import app.blinkers.data.source.LedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ControllerViewModel(
    private val ledRepository: LedRepository,
    private val brainWavesRepository: BrainWavesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel(), SerialListener {

    init {
        Log.d("SERV", "Started")
    }

    private var deviceRepository: BrainWavesRepository? = null

    private var connected = false
    private var notificationMsg: String? = null

    private val _forceUpdate = MutableLiveData<Boolean>(false)

    private val _ledOneStatusLabel = MutableLiveData<Int>()
    val ledOneStatusLabel: LiveData<Int> = _ledOneStatusLabel

    private val _dataLoading = MutableLiveData<Boolean>()
    val dataLoading: LiveData<Boolean> = _dataLoading

    var observeBrainWaves: LiveData<Result<BrainWaves>> = brainWavesRepository.observe()

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
        ledRepository.updateLed(Led.RED, isOn)
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


    /**
     * SerialListener
     */
    override fun onSerialConnect() {
        if (connected) {
            viewModelScope.launch {
                _serialConnectEvent.value = true
            }
        }
    }

    override fun onSerialConnectError(e: Exception) {
        if (connected) {
            viewModelScope.launch {
                _serialConnectErrorEvent.value = Event(e.toString())
            }
        }
    }

    override fun onSerialRead(data: ByteArray) {
        if (connected) {
            viewModelScope.launch {
                _serialReadEvent.value = Event(data)
            }
        }
    }

    override fun onSerialIoError(e: Exception) {
        if (connected) {
            viewModelScope.launch {
                _serialConnectErrorEvent.value = Event(e.toString())
            }
        }
    }
}