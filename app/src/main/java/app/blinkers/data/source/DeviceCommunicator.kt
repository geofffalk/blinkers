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
interface DeviceCommunicator {

    fun connect(context: Context, device: BluetoothDevice)
    fun disconnect()
    suspend fun updateLed(isOn: Boolean)
    fun observeLatestDeviceState(): LiveData<Result<DeviceState>>
    fun setPhaseTime(phase: Int, seconds: Int)
    fun setSpeed(speed: Int)
}

object DefaultDeviceCommunicator  : DeviceCommunicator, Runnable {

    private val BLUETOOTH_SPP =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnected = false
    private lateinit var socket: BluetoothSocket
    private lateinit var device: BluetoothDevice
    private lateinit var context: Context
    private val buffer: ByteArray = ByteArray(1024)
    private val disconnectBroadcastReceiver: BroadcastReceiver
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _currentDeviceState = MutableLiveData<Result<DeviceState>>()
    private val currentDeviceState: LiveData<Result<DeviceState>> = _currentDeviceState


    init {
        disconnectBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _currentDeviceState.postValue(Result.Error(IOException("background disconnect")))
                disconnect()
            }
        }
    }

    override fun connect(context: Context, device: BluetoothDevice) {
        if (isConnected) {
            _currentDeviceState.postValue(Result.Error(IOException("Already connected")))
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

    override fun disconnect() {
        try {
            socket.close()
        } catch (e: Exception) {
            _currentDeviceState.postValue(Result.Error(e))
        }

        try {
            context.unregisterReceiver(disconnectBroadcastReceiver)
        } catch (ignored: Exception) {
        }

        isConnected = false
    }

    override suspend fun updateLed(isOn: Boolean) = withContext(ioDispatcher) {
        try {
            Timber.d("Switched on is %s", isOn)
            socket.outputStream.write(if (isOn) "1".toByteArray() else "0".toByteArray())
        } catch (e: Exception) {
            _currentDeviceState.postValue(Result.Error(e))
        }
    }

    override fun run() {
        try {
            socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
            socket.connect()
            _currentDeviceState.postValue(Result.Success(DeviceState()))
            isConnected = true
        } catch (e: Exception) {
            _currentDeviceState.postValue(Result.Error(e))
            try {
                disconnect()
            } catch (e: Exception) {
                _currentDeviceState.postValue(Result.Error(e))
            }
        }

        var blinkersState = DeviceState(System.currentTimeMillis(), -1,
            EEGSnapshot(-1,-1,-1,-1,-1,-1,-1,-1,-1))
        var numBytes: Int // bytes returned from read()
        var currentString = StringBuilder("")
        var buildingBrainWaves = false
        var buildingLedStatus = false

        try {
            while (socket.isConnected) {
                numBytes = socket.inputStream.read(buffer)
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
                            _currentDeviceState.postValue(Result.Success(blinkersState))
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
                                eegSnapshot = EEGSnapshot(
                                signalStrength = waves[0],
                                delta = waves[3],
                                theta = waves[4],
                                lowAlpha = waves[5],
                                highAlpha = waves[6],
                                lowBeta = waves[7],
                                highBeta = waves[8],
                                lowGamma = waves[9],
                                highGamma = waves[10]))
                            _currentDeviceState.postValue(Result.Success(blinkersState))
                        }
                        else -> {
                            if (buildingBrainWaves || buildingLedStatus) currentString.append(it)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            _currentDeviceState.postValue(Result.Error(e))
        }
    }

    override fun observeLatestDeviceState(): LiveData<Result<DeviceState>> = currentDeviceState

    override fun setPhaseTime(phase: Int, seconds: Int) {
        socket.outputStream.write(phase.toString().toByteArray())
    }

    override fun setSpeed(speed: Int) {
        socket.outputStream.write(arrayOf("s", "m", "f")[speed].toByteArray())
    }
}