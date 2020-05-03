package app.blinkers

import androidx.lifecycle.*
import app.blinkers.data.LedRepository
import app.blinkers.data.Led
import app.blinkers.data.Result
import kotlinx.coroutines.launch
import timber.log.Timber

class ControllerViewModel(
    private val ledRepository: LedRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

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

    private val _snackbarText = MutableLiveData<Event<Int>>()
    val snackbarText: LiveData<Event<Int>> = _snackbarText

    private val isDataLoadingError = MutableLiveData<Boolean>()

    init {
        _ledOneStatusLabel.value =  R.string.off
        loadLedStatus(true)
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


}