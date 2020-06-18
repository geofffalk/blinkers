package app.blinkers.data

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData

interface IORepository {

    fun observe(): LiveData<Result<ByteArray>>
    suspend fun write(data: ByteArray)

}