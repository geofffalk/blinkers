package app.blinkers.data.source

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.blinkers.INTENT_ACTION_DISCONNECT
import app.blinkers.data.DeviceState
import app.blinkers.data.EEGSnapshot
import app.blinkers.data.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.lang.Boolean.TRUE
import java.util.*
import java.util.concurrent.Executors


interface DeviceCommunicator {

    fun connect(context: Context, deviceAddress: String)
    fun disconnect()
    suspend fun updateLed(isOn: Boolean)
    fun observeLatestDeviceState(): LiveData<Result<DeviceState>>
    fun setPhaseTime(phase: Int, seconds: Int)
    fun setSpeed(speed: Int)
    fun setBrightness(brightness: Int)
    fun startProgram(sessionTime: Int, paletteCode: Int, startStage: Int, endStage: Int, brightness: Int)
    fun stopProgram()
}

data class GattCharacteristics(val modelNumber: BluetoothGattCharacteristic,
                               val serialPort: BluetoothGattCharacteristic,
                               val command: BluetoothGattCharacteristic
)

@SuppressLint("StaticFieldLeak")
object DefaultDeviceCommunicator  : DeviceCommunicator, Runnable {

    private val MAX_CHARACTERISTIC_LENGTH = 12
    const val SerialPortUUID = "0000dfb1-0000-1000-8000-00805f9b34fb"
    const val CommandUUID = "0000dfb2-0000-1000-8000-00805f9b34fb"
    const val ModelNumberStringUUID = "00002a24-0000-1000-8000-00805f9b34fb"
    private lateinit var gattCharacteristics: GattCharacteristics
    private lateinit var activeCharacteristic: BluetoothGattCharacteristic
    private val gattCharacteristicsList = ArrayList<GattCharacteristics>()

    const val START_COMMAND = 200.toByte()
    const val STOP_COMMAND = 201.toByte()


    private var isConnected = false
    private var deviceAddress: String? = null
    private var bluetoothManager:  BluetoothManager? = null
    private var bluetoothAdapter:  BluetoothAdapter? = null
    private val baudRate = 115200
    private val password = "AT+PASSWORD=2345\r\n"
    private val baudRateBuffer = "AT+UART=${baudRate}\r\n"
//    private lateinit var socket: BluetoothSocket
//    private lateinit var device: BluetoothDevice
    private  var btGatt: BluetoothGatt? = null
    private lateinit var context: Context
    private val buffer: ByteArray = ByteArray(1024)
    private val disconnectBroadcastReceiver: BroadcastReceiver
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var isWriting = false
    private const val WRITE_NEW_CHARACTERISTIC = -1

    data class UglyMutableCharacteristicHolder(var characteristic: BluetoothGattCharacteristic, var characteristicValue: String)
    private val ringBuffer = RingBuffer<UglyMutableCharacteristicHolder>(8)

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

    override fun connect(context: Context, deviceAddress: String) {
        if (isConnected) {
            _currentDeviceState.postValue(Result.Error(IOException("Already connected")))
            return
        }
        this.deviceAddress = deviceAddress
        this.context = context


        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
            bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
            if (bluetoothManager == null) {
                Timber.e("Unable to initialize BluetoothManager.")
            } else {
                bluetoothAdapter = bluetoothManager!!.adapter
                if (bluetoothManager == null) {
                    Timber.e("Unable to obtain a BluetoothAdapter."
                    )
                }
            }

        context.registerReceiver(
            disconnectBroadcastReceiver,
            IntentFilter(INTENT_ACTION_DISCONNECT)
        )
        Executors.newSingleThreadExecutor().submit(this)
    }

    override fun disconnect() {
        try {
            btGatt?.disconnect()
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
//            btGatt.
//            btGatt.write(if (isOn) "1".toByteArray() else "0".toByteArray())
        } catch (e: Exception) {
            _currentDeviceState.postValue(Result.Error(e))
        }
    }

    override fun run() {
        try {
            if (bluetoothAdapter == null || deviceAddress == null) {
                _currentDeviceState.postValue(Result.Error(Exception("No bluetooth adapter")))
            }

            // Previously connected device.  Try to reconnect.
            if (btGatt != null) {
                Timber.d(

                    "Trying to use an existing mBluetoothGatt for connection."
                )
                if (btGatt?.connect() == TRUE) {
                    _currentDeviceState.postValue(Result.Success(DeviceState()))
                    isConnected = true
                }
            } else {
                val device: BluetoothDevice? = bluetoothAdapter!!.getRemoteDevice(deviceAddress)
                if (device == null) {
                    _currentDeviceState.postValue(Result.Error(Exception("Cannot find device")))
                } else {
                    // We want to directly connect to the device, so we are setting the autoConnect
                    // parameter to false.
                    btGatt = device.connectGatt(context, false, gattCallback)
                    Timber.d(
                        "Trying to create a new connection."
                    )

                }
            }
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

//        try {
//            while (socket.isConnected) {
//                numBytes = socket.inputStream.read(buffer)
//                val data = String(buffer.copyOf(numBytes))
//
//                data.forEach {
//                    when (it) {
//                        '*' -> {
//                            buildingLedStatus = true
//                            currentString = StringBuilder("")
//                        }
//                        '%' -> {
//                            buildingLedStatus = false
//                            blinkersState = blinkersState.copy(
//                                timestamp = System.currentTimeMillis(),
//                                ledStatus = currentString.toString().toInt())
//                            _currentDeviceState.postValue(Result.Success(blinkersState))
//                        }
//                        '!' -> {
//                            buildingBrainWaves = true
//                            currentString = StringBuilder("")
//                        }
//                        '~' -> {
//                            buildingBrainWaves = false
//                            val waves = currentString.split(",").map { it.toInt() }
//                            blinkersState = blinkersState.copy(
//                                timestamp = System.currentTimeMillis(),
//                                eegSnapshot = EEGSnapshot(
//                                signalStrength = waves[0],
//                                delta = waves[3],
//                                theta = waves[4],
//                                lowAlpha = waves[5],
//                                highAlpha = waves[6],
//                                lowBeta = waves[7],
//                                highBeta = waves[8],
//                                lowGamma = waves[9],
//                                highGamma = waves[10]))
//                            _currentDeviceState.postValue(Result.Success(blinkersState))
//                        }
//                        else -> {
//                            if (buildingBrainWaves || buildingLedStatus) currentString.append(it)
//                        }
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            _currentDeviceState.postValue(Result.Error(e))
//        }
    }

    override fun observeLatestDeviceState(): LiveData<Result<DeviceState>> = currentDeviceState

    override fun setPhaseTime(phase: Int, seconds: Int) {
      //  socket.outputStream.write(phase.toString().toByteArray())
    }

    override fun setSpeed(speed: Int) {
      //  socket.outputStream.write(arrayOf("s", "m", "f")[speed].toByteArray())
    }

    override fun setBrightness(brightness: Int) {
        try {
            if (isConnected) {
                val brightnessByte = (1.coerceAtLeast(10.coerceAtMost(brightness)) + 50).toByte()

                activeCharacteristic.value = byteArrayOf(brightnessByte)

             Timber.d("WRITING TO DEVICE: ${activeCharacteristic.getStringValue(0)}")
                btGatt?.writeCharacteristic(activeCharacteristic)
            }
        } catch (exception: IOException) {
            Timber.d("Comms failure")
        }
    }

    override fun stopProgram() {
        try {
            if (isConnected) {
                activeCharacteristic.value = byteArrayOf(STOP_COMMAND)

                //The character size of TI CC2540 is limited to 17 bytes, otherwise characteristic can not be sent properly,
                //so String should be cut to comply this restriction. And something should be done here:
                Timber.d("WRITING TO DEVICE: ${activeCharacteristic.getStringValue(0)}")
//
//                //As the communication is asynchronous content string and characteristic should be pushed into an ring buffer for further transmission
//                ringBuffer.push(
//                    UglyMutableCharacteristicHolder(
//                        activeCharacteristic,
//                        activeCharacteristic.getStringValue(0)
//                    )
//                )
//                Timber.d("mCharacteristicRingBufferlength:" + ringBuffer.size())

//
//                //The progress of onCharacteristicWrite and writeCharacteristic is almost the same. So callback function is called directly here
//                //for details see the onCharacteristicWrite function
                btGatt?.writeCharacteristic(activeCharacteristic)

//                gattCallback.onCharacteristicWrite(
//                    btGatt,
//                    activeCharacteristic,
//                    WRITE_NEW_CHARACTERISTIC
//                )
            }

            //     socket.outputStream.write("${p0Millis},${p1Millis},${p2Millis},${p3Millis},${rMillis}*".toByteArray())
        } catch (exception: IOException) {
            Timber.d("Comms failure")
        }
    }

    override fun startProgram(
        sessionTime: Int,
        paletteCode: Int,
        startStage: Int,
        endStage: Int,
        brightness: Int
    ) {
        try {
            if (isConnected) {

                // phase time converted to byte range 100 - 159
                val sessionTimeByte = (sessionTime + 100).toByte()

                // startStage converted to byte range 10 - 17
                val startStageByte = ((7.coerceAtMost(0.coerceAtLeast(startStage))) + 10).toByte()

                // endStage converted to byte range 20 - 27
                val endStageByte = ((7.coerceAtMost(0.coerceAtLeast(endStage))) + 20).toByte()

                // paletteCode converted to byte range 30 - 37
                val paletteCodeByte = ((4.coerceAtMost(0.coerceAtLeast(paletteCode))) + 30).toByte()

                val brightnessByte = ((9.coerceAtMost(0.coerceAtLeast(brightness))) + 50).toByte()

                activeCharacteristic.value = byteArrayOf(sessionTimeByte, paletteCodeByte, startStageByte, endStageByte, brightnessByte, START_COMMAND)

                //The character size of TI CC2540 is limited to 17 bytes, otherwise characteristic can not be sent properly,
                //so String should be cut to comply this restriction. And something should be done here:
                Timber.d("WRITING TO DEVICE: ${activeCharacteristic.getStringValue(0)}")
//
//                //As the communication is asynchronous content string and characteristic should be pushed into an ring buffer for further transmission
//                ringBuffer.push(
//                    UglyMutableCharacteristicHolder(
//                        activeCharacteristic,
//                        activeCharacteristic.getStringValue(0)
//                    )
//                )
//                Timber.d("mCharacteristicRingBufferlength:" + ringBuffer.size())

//
//                //The progress of onCharacteristicWrite and writeCharacteristic is almost the same. So callback function is called directly here
//                //for details see the onCharacteristicWrite function
                btGatt?.writeCharacteristic(activeCharacteristic)

//                gattCallback.onCharacteristicWrite(
//                    btGatt,
//                    activeCharacteristic,
//                    WRITE_NEW_CHARACTERISTIC
//                )
            }

       //     socket.outputStream.write("${p0Millis},${p1Millis},${p2Millis},${p3Millis},${rMillis}*".toByteArray())
        } catch (exception: IOException) {
            Timber.d("Comms failure")
        }
    }

    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val intentAction: String
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.d("Connected to GATT server.")
                _currentDeviceState.postValue(Result.Success(DeviceState()))
                isConnected = true
                // Attempts to discover services after successful connection.
                Timber.d("Attempting to start service discovery: ${btGatt?.discoverServices()}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.d("Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                // Show all the supported services and characteristics on the user interface.
                for (gattService in gatt.services) {
                    Timber.d(
                        "ACTION_GATT_SERVICES_DISCOVERED  " +
                                gattService.uuid.toString()
                    )
                }
                getGattServices(gatt.services)
                Timber.d("SERVICES AVAILABLE: ${gatt.services}")
            } else {
                Timber.w("onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.d("Characteristic read: $characteristic, with UUID ${characteristic.uuid}")
                if (activeCharacteristic == gattCharacteristics.modelNumber) {
                    btGatt?.setCharacteristicNotification(activeCharacteristic, false)
                    activeCharacteristic = gattCharacteristics.command
                    activeCharacteristic.setValue(password)
                    btGatt?.writeCharacteristic(activeCharacteristic)
                    activeCharacteristic.setValue(baudRateBuffer)
                    btGatt?.writeCharacteristic(activeCharacteristic)
                    activeCharacteristic= gattCharacteristics.serialPort
                    gattCharacteristics.serialPort.writeType = WRITE_TYPE_DEFAULT
                    btGatt?.setCharacteristicNotification(activeCharacteristic, true)
                    isConnected = true
                } else if (activeCharacteristic == gattCharacteristics.serialPort) {
                    Timber.d("Serial received data: ${characteristic.getStringValue(0)}")
                }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {

            synchronized(this) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (ringBuffer.isEmpty) isWriting = false else {
                        val charHolder = ringBuffer.next()
                        if (charHolder.characteristicValue.length > MAX_CHARACTERISTIC_LENGTH) {
                            try {
                                charHolder.characteristic.value =
                                    charHolder.characteristicValue.substring(
                                        0,
                                        MAX_CHARACTERISTIC_LENGTH
                                    ).toByteArray(
                                        charset("ISO-8859-1")
                                    )
                            } catch (e: UnsupportedEncodingException) {
                                // this should never happen because "US-ASCII" is hard-coded.
                                throw IllegalStateException(e)
                            }

                            if (btGatt?.writeCharacteristic(charHolder.characteristic) == TRUE) {
                                Timber.d("Write characteristic operation success: ${charHolder.characteristic.value}")
                            } else {
                                Timber.d("Write characteristic operation failure: ${charHolder.characteristic.value}")
                            }
                            charHolder.characteristicValue =
                                charHolder.characteristicValue.substring(0, MAX_CHARACTERISTIC_LENGTH)
                        } else {

                            try {
                                charHolder.characteristic.value =
                                    charHolder.characteristicValue.toByteArray(charset("ISO-8859-1"))
                            } catch (e: UnsupportedEncodingException) {
                                // this should never happen because "US-ASCII" is hard-coded.
                                throw IllegalStateException(e)
                            }

                            if (btGatt?.writeCharacteristic(charHolder.characteristic) == TRUE) {
                                Timber.d(
                                    "writeCharacteristic init ${charHolder.characteristic.value} : success"
                                )
                            } else {
                                Timber.d(
                                    "writeCharacteristic init ${charHolder.characteristic.value} : failure"
                                )
                            }
                            charHolder.characteristicValue = ""

//	            			System.out.print("before pop:");
//	            			System.out.println(mCharacteristicRingBuffer.size());

//	            			System.out.print("before pop:");
//	            			System.out.println(mCharacteristicRingBuffer.size());
                            ringBuffer.pop()
//	            			System.out.print("after pop:");
//	            			System.out.println(mCharacteristicRingBuffer.size());
                        }

                    }

                    ringBuffer.next()
                }
                else if(status == WRITE_NEW_CHARACTERISTIC) {
                    if (!ringBuffer.isEmpty && !isWriting) {
                        val charHolder = ringBuffer.next()
                        if (charHolder.characteristicValue.length > MAX_CHARACTERISTIC_LENGTH) {
                            try {
                                charHolder.characteristic.value = charHolder.characteristicValue.substring(
                                    0,
                                    MAX_CHARACTERISTIC_LENGTH
                                ).toByteArray(charset("ISO-8859-1"))
                            } catch (e: UnsupportedEncodingException) {
                                // this should never happen because "US-ASCII" is hard-coded.
                                throw java.lang.IllegalStateException(e)
                            }
                            if (btGatt?.writeCharacteristic(charHolder.characteristic) == TRUE) {
                                Timber.d("writeCharacteristic init ${charHolder.characteristic.value} : success")
                            } else {
                                Timber.d("writeCharacteristic init ${charHolder.characteristic.value} : failure")
                            }
                            charHolder.characteristicValue =
                                charHolder.characteristicValue.substring(MAX_CHARACTERISTIC_LENGTH)
                        } else {
                            try {
                                charHolder.characteristic.value = charHolder.characteristicValue.toByteArray(charset("ISO-8859-1"))
                            } catch (e: UnsupportedEncodingException) {
                                // this should never happen because "US-ASCII" is hard-coded.
                                throw java.lang.IllegalStateException(e)
                            }
                            if (btGatt?.writeCharacteristic(charHolder.characteristic) == TRUE) {
                                Timber.d(
                                    "writeCharacteristic init ${charHolder.characteristic.value} :success"
                                )
                                //	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[0]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[1]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[2]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[3]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[4]);
//	            	        	System.out.println((byte)bluetoothGattCharacteristicHelper.mCharacteristic.getValue()[5]);
                            } else {
                                Timber.d(
                                    "writeCharacteristic init ${charHolder.characteristic.value} :failed"
                                )
                            }
                            charHolder.characteristicValue = ""

//		            			System.out.print("before pop:");
//		            			System.out.println(mCharacteristicRingBuffer.size());
                            ringBuffer.pop()
                            //		            			System.out.print("after pop:");
//		            			System.out.println(mCharacteristicRingBuffer.size());
                        }
                    }

                    isWriting = true

                    //clear the buffer to prevent the lock of the mIsWritingCharacteristic
                    if(ringBuffer.isFull) {
                        ringBuffer.clear()
                        isWriting = false
                    } else {} // really?
                }
                else {
                    ringBuffer.clear()
                    Timber.d("onCharacteristicWrite fail: ${characteristic?.value}")
                    Timber.d("$status")
            }
            }

        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Timber.d("Characteristic changed: $characteristic")
        }
    }

    private fun getGattServices(services: List<BluetoothGattService>?) {
        if (services == null) return

        var modelNumberChar: BluetoothGattCharacteristic? = null
        var serialPortChar: BluetoothGattCharacteristic? = null
        var commandChar: BluetoothGattCharacteristic? = null

        services.forEach { service ->
            service.characteristics.map {
                when(it.uuid.toString()) {
                    ModelNumberStringUUID -> modelNumberChar = it
                    SerialPortUUID -> serialPortChar = it
                    CommandUUID -> commandChar = it
                }
            }
        }

        serialPortChar!!.writeType = WRITE_TYPE_DEFAULT
        gattCharacteristics = GattCharacteristics(modelNumberChar!!, serialPortChar!!, commandChar!!)
        activeCharacteristic = modelNumberChar!!
        btGatt?.setCharacteristicNotification(activeCharacteristic, true)
        btGatt?.readCharacteristic(modelNumberChar)
    }
}

