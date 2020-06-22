package app.blinkers.data.source

import android.bluetooth.BluetoothSocket
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.blinkers.data.BrainWaves
import app.blinkers.data.Led
import app.blinkers.data.LedStatus
import app.blinkers.data.Result
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException
import java.lang.NullPointerException
import kotlin.coroutines.CoroutineContext

class BluetoothDataSource(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)  : BrainWavesLiveDataSource, LedDataSource, CoroutineScope {

    private val job = Job()
    private var socket: BluetoothSocket? = null
    private val buffer: ByteArray = ByteArray(1024)

    private val _currentBrainWaves = MutableLiveData<Result<BrainWaves>>()
    private val _ledStatus = MutableLiveData<Result<LedStatus>>()

    fun connectToSocket(socket: BluetoothSocket) {
        socket.connect()
        this.socket = socket

        launch {
            val inputStream = socket.inputStream
            var brainWaveString = ""
            var buildingWaveString = false
            val resetBrainWaveString: () -> Unit = {
                brainWaveString = ""
                buildingWaveString = false
            }


            var ledStatusString = ""
            var buildingLedStatus = false
            val resetLedStatus: () -> Unit = {
                ledStatusString = ""
                buildingLedStatus = false
            }

            try {
                var numBytes: Int // bytes returned from read()
                while (true) {
                    numBytes = inputStream.read(buffer)
                    val data = String(buffer.copyOf(numBytes))

                    when {
                        data.contains("*") -> {
                            buildingLedStatus = true
                            ledStatusString = data.substringAfter("*")
                            if (data.contains("%")) {
                                postLedStatus(ledStatusString.substringBefore("%"))
                                resetLedStatus()
                            }
                        }
                        data.contains("%") -> {
                            postLedStatus(data.substringBefore("%"))
                            resetLedStatus()
                        }

                        data.contains("!") -> {
                            buildingWaveString = true;
                            brainWaveString = data.substringAfter("!")
                            if (data.contains("~")) {
                                postBrainWave(brainWaveString.substringBefore("~"))
                                resetBrainWaveString()
                            }
                        }
                        data.contains("~") -> {
                            brainWaveString += data.substringBefore("~")
                            postBrainWave(brainWaveString)
                            resetBrainWaveString()
                        }
                        buildingWaveString -> {
                            brainWaveString += data
                        }
                        buildingLedStatus -> {
                            ledStatusString += data

                        }
                    }
                }

            } catch (e: Exception) {
                _currentBrainWaves.postValue(Result.Error(e))
                job.cancel()
            }
        }
    }

    private fun postLedStatus(ledStatus: String) {
        _ledStatus.postValue(Result.Success(LedStatus(Led.RED, ledStatus == "1")))
    }

    private fun postBrainWave(brainWaveString: String) {
        val values = brainWaveString.split(",").map {
            it.toFloat() / 10000
        }
        _currentBrainWaves.postValue(
            Result.Success(
                BrainWaves(
                    signalStrength = values[0],
                    delta = values[3],
                    theta = values[4],
                    lowAlpha = values[5],
                    highAlpha = values[6],
                    lowBeta = values[7],
                    highBeta = values[8],
                    lowGamma = values[9],
                    highGamma = values[10]
                )
            )
        )
    }

    fun disconnect() {
        socket?.close()
    }

    override fun observeBrainWaves(): LiveData<Result<BrainWaves>> = _currentBrainWaves

    override fun observeLed(): LiveData<Result<LedStatus>> = _ledStatus

    override suspend fun updateLed(led: Led, isOn: Boolean) = withContext(ioDispatcher) {
        try {
            Timber.d("Switched on is %s", isOn)
            socket!!.outputStream.write(if (isOn) "1".toByteArray() else "0".toByteArray())
        } catch (e: NullPointerException) {
            _ledStatus.postValue(Result.Error(IOException("Socket is null")))
        }
            catch (e: Exception) {
            _ledStatus.postValue(Result.Error(e))
        }
    }

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.IO

}