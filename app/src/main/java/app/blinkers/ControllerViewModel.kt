package app.blinkers

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import app.blinkers.data.IORepository
import app.blinkers.data.LedRepository
import app.blinkers.data.Led
import app.blinkers.data.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ControllerViewModel(
    private val ledRepository: LedRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel(), SerialListener {

    init {
        Log.d("SERV", "Started")
    }

    private var deviceRepository: IORepository? = null

    private var connected = false
    private var notificationMsg: String? = null

    private val _forceUpdate = MutableLiveData<Boolean>(false)

    private val _items: LiveData<List<Led>> = _forceUpdate.switchMap { forceUpdate ->
        if (forceUpdate) {
            _dataLoading.value = true;
            viewModelScope.launch {
                ledRepository.getLeds();
                _dataLoading.value = false;
            }
        }
        ledRepository.observeLeds().distinctUntilChanged().switchMap {
           updateLedStatus(it)
        }

    }

    val items: LiveData<List<LedViewState>> = _items.map {
        it.map {
            val state =  if (it.isOn) "ON" else "OFF"
            LedViewState(it.id, state, it.isOn)
        }
    }

    private val _ledOneStatusLabel = MutableLiveData<Int>()
    val ledOneStatusLabel: LiveData<Int> = _ledOneStatusLabel

    private val _dataLoading = MutableLiveData<Boolean>()
    val dataLoading: LiveData<Boolean> = _dataLoading

    var readData: LiveData<Result<ByteArray>> = MutableLiveData()

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

    fun connect(notificationMsg: String?, device: BluetoothDevice) {
        connected = true;
        this.notificationMsg = notificationMsg;

    }

    fun setRepo(deviceRepo: IORepository) {
        deviceRepository = deviceRepo

        readData = deviceRepo.observe()
    }


    fun loadLedStatus(forceUpdate: Boolean) {
        _forceUpdate.value = forceUpdate
    }

    fun switchLed(id: Int, isOn: Boolean) = viewModelScope.launch {
        ledRepository.setLed(id, isOn)
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

    fun writeData(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceRepository?.write(data)
        }
    }

}