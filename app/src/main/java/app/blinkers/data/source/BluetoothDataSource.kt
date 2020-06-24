package app.blinkers.data.source

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.blinkers.INTENT_ACTION_DISCONNECT
import app.blinkers.data.BrainWaves
import app.blinkers.data.Led
import app.blinkers.data.LedStatus
import app.blinkers.data.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

class BluetoothDataSource(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)  : BrainWavesLiveDataSource, LedDataSource, Runnable {

    private val BLUETOOTH_SPP =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnected = false
    private lateinit var socket: BluetoothSocket
    private lateinit var device: BluetoothDevice
    private lateinit var context: Context
    private val buffer: ByteArray = ByteArray(1024)
    private val disconnectBroadcastReceiver: BroadcastReceiver

    private val _currentBrainWaves = MutableLiveData<Result<BrainWaves>>()
    private val _ledStatus = MutableLiveData<Result<LedStatus>>()
    private val _connectionStatus = MutableLiveData<Result<String>>();

    init {
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _connectionStatus.postValue(Result.Error(IOException("background disconnect")))
                disconnect() // disconnect now, else would be queued until UI re-attached
            }
        }
    }

    fun connect(context: Context, device: BluetoothDevice) {
        if (isConnected) {
            _connectionStatus.postValue(Result.Error(IOException("Already connected")))
            return
        }
        this.device = device
        this.context = context
        context.registerReceiver(
            disconnectBroadcastReceiver,
            IntentFilter(INTENT_ACTION_DISCONNECT)
        )
        Executors.newSingleThreadExecutor().submit(this)
    }

    fun disconnect() {
            try {
                socket.close()
            } catch (e: Exception) {
                _connectionStatus.postValue(Result.Error(e))
            }

        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: java.lang.Exception) {
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

    override fun observeBrainWaves(): LiveData<Result<BrainWaves>> = _currentBrainWaves

    override fun observeLed(): LiveData<Result<LedStatus>> = _ledStatus

    override suspend fun updateLed(led: Led, isOn: Boolean) = withContext(ioDispatcher) {
        try {
            Timber.d("Switched on is %s", isOn)
            socket.outputStream.write(if (isOn) "1".toByteArray() else "0".toByteArray())
        } catch (e: NullPointerException) {
            _ledStatus.postValue(Result.Error(IOException("Socket is null")))
        }
            catch (e: Exception) {
            _ledStatus.postValue(Result.Error(e))
        }
    }

    override fun run() {
        try {
            socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            socket.connect()
            _connectionStatus.postValue(Result.Success("$device is connected successfully"))
        } catch (e: Exception) {
            _connectionStatus.postValue(Result.Error(e))
            try {
                socket.close()
            } catch (e: Exception) {
                _connectionStatus.postValue(Result.Error(e))
            }
            return
        }

        isConnected = true

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
                if (isConnected) {
                    numBytes = this@BluetoothDataSource.socket!!.inputStream.read(buffer)
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
            }

        } catch (e: Exception) {
            _currentBrainWaves.postValue(Result.Error(e))
        }
    }

    fun observeConnectionStatus(): LiveData<Result<String>> = _connectionStatus
}