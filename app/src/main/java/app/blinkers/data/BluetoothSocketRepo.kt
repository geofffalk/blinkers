package app.blinkers.data

import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

data class BrainWaves(val signalStrength: Float, val delta: Float, val theta: Float, val lowAlpha: Float, val highAlpha: Float, val lowBeta: Float, val highBeta: Float, val lowGamma: Float, val highGamma: Float)


class BluetoothSocketRepo(
    socket: BluetoothSocket,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IORepository {

    private val inputStream: InputStream = socket.inputStream
    private val outputStream: OutputStream = socket.outputStream
    private val buffer: ByteArray = ByteArray(1024)

    override fun observe() : LiveData<Result<BrainWaves>> =  liveData(ioDispatcher) {

        var brainWaveString = ""
        var buildingWaveString = false

        try {
            var numBytes: Int // bytes returned from read()
            while (true) {
                numBytes = inputStream.read(buffer)
                val data = String(buffer.copyOf(numBytes))
                Log.d("DATA RECEIVED: ", data)

                when {
                    data.contains("!") -> {
                        buildingWaveString = true;
                        brainWaveString = data.substringAfter("!")
                    }
                    data.contains("~") -> {
                        brainWaveString += data.substringBefore("~")
                        buildingWaveString = false
                        val values = brainWaveString.split(",").map {
                            it.toFloat() / 10000
                        }
                        emit(Result.Success(BrainWaves(
                            signalStrength = values[0],
                            delta = values[3],
                            theta = values[4],
                            lowAlpha = values[5],
                            highAlpha = values[6],
                            lowBeta = values[7],
                            highBeta = values[8],
                            lowGamma = values[9],
                            highGamma = values[10]

                        )))
                        brainWaveString = ""
                    }
                    buildingWaveString -> {
                        brainWaveString += data
                    }
                }
            }

            } catch (e: Exception) {
                emit(Result.Error(e))
            }
        }

    override suspend fun write(data: ByteArray) = withContext(ioDispatcher) {
        try {
            outputStream.write(data)
        } catch (e: Exception) {
      //
        }
    }
}