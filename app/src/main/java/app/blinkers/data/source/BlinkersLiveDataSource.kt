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
import app.blinkers.data.*
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors

object BlinkersLiveDataSource  : BlinkersDataSource, Runnable {

    private val BLUETOOTH_SPP =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnected = false
    private lateinit var socket: BluetoothSocket
    private lateinit var device: BluetoothDevice
    private lateinit var context: Context
    private val buffer: ByteArray = ByteArray(1024)
    private val disconnectBroadcastReceiver: BroadcastReceiver
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _currentBlinkersState = MutableLiveData<Result<BlinkersState>>()
    val currentBlinkersState: LiveData<Result<BlinkersState>> = _currentBlinkersState

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

    suspend fun updateLed(isOn: Boolean) = withContext(ioDispatcher) {
        try {
            Timber.d("Switched on is %s", isOn)
            socket.outputStream.write(if (isOn) "1".toByteArray() else "0".toByteArray())
        } catch (e: Exception) {
            _currentBlinkersState.postValue(Result.Error(e))
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
        }

        isConnected = true

        var blinkersState = BlinkersState(System.currentTimeMillis(), -1, -1,-1,-1,-1,-1,-1,-1,-1,-1)
        var numBytes: Int // bytes returned from read()
        var currentString = StringBuilder("")
        var buildingBrainWaves = false
        var buildingLedStatus = false

        try {
            while (true) {
                if (isConnected) {
                    numBytes = this@BlinkersLiveDataSource.socket.inputStream.read(buffer)
                    val data = String(buffer.copyOf(numBytes))

                    data.forEach {
                        when (it) {
                            '*' -> {
                                buildingLedStatus = true
                                currentString = StringBuilder("")
                            }
                            '%' -> {
                                buildingLedStatus = false
                                blinkersState = blinkersState.copy(
                                    timestamp = System.currentTimeMillis(),
                                    ledStatus = currentString.toString().toInt())
                                _currentBlinkersState.postValue(Result.Success(blinkersState))
                            }
                            '!' -> {
                                buildingBrainWaves = true
                                currentString = StringBuilder("")
                            }
                            '~' -> {
                                buildingBrainWaves = false
                                val waves = currentString.split(",").map { it.toInt() }
                                blinkersState = blinkersState.copy(
                                    timestamp = System.currentTimeMillis(),
                                    signalStrength = waves[0],
                                    delta = waves[3],
                                    theta = waves[4],
                                    lowAlpha = waves[5],
                                    highAlpha = waves[6],
                                    lowBeta = waves[7],
                                    highBeta = waves[8],
                                    lowGamma = waves[9],
                                    highGamma = waves[10])
                                _currentBlinkersState.postValue(Result.Success(blinkersState))
                            }
                            else -> {
                                if (buildingBrainWaves || buildingLedStatus) currentString.append(it)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            _currentBlinkersState.postValue(Result.Error(e))
        }
    }

    fun observeConnectionStatus(): LiveData<Result<String>> = _connectionStatus

    override fun observeLastBlinkerState(): LiveData<Result<BlinkersState>> = currentBlinkersState

    override suspend fun saveBlinkerState(blinkersState: BlinkersState) {
        TODO("Not yet implemented")
    }

    override suspend fun getBlinkerStates(): Result<List<BlinkersState>> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteAllStates() {
        TODO("Not yet implemented")
    }
}