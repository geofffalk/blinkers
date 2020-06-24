package app.blinkers

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import app.blinkers.data.source.BluetoothStatusRepository
import app.blinkers.data.source.BrainWavesRepository
import app.blinkers.data.source.LedRepository

@Suppress("UNCHECKED_CAST")
class ViewModelFactory constructor(
    private val ledRepository: LedRepository,
    private val brainWavesRepository: BrainWavesRepository,
    private val bluetoothStatusRepository: BluetoothStatusRepository,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ) = with(modelClass) {
        when {
            isAssignableFrom(ControllerViewModel::class.java) ->
                ControllerViewModel(ledRepository, brainWavesRepository, bluetoothStatusRepository, handle)
            else ->
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    } as T
}
