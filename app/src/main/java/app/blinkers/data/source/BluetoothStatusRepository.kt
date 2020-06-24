package app.blinkers.data.source

import androidx.lifecycle.LiveData

class BluetoothStatusRepository(
    private val bluetoothDataSource: BluetoothDataSource
) {

    fun observeConnectionStatus() = bluetoothDataSource.observeConnectionStatus()
}