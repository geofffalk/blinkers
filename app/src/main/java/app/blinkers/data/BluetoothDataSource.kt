package app.blinkers.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BluetoothDataSource internal constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DataSource {

    var deviceLeds: HashMap<Int, Led> = hashMapOf(
        Pair(0, Led(0, false)),
        Pair(1, Led(1, false)),
        Pair(2, Led(2, false)),
        Pair(3, Led(3, false)))

    val rdm = Random(0)

    init {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                val id = Random(0).nextInt(0, 3)
                setLed(id, deviceLeds[id]?.isOn == false)
                mainHandler.postDelayed(this, 3000)
            }

        })
    }

    private val _leds = MutableLiveData(deviceLeds)
    val leds: LiveData<List<Led>> = _leds.map { ArrayList(it.values) };

    override fun observeLeds(): LiveData<Result<List<Led>>> {
        return leds.map {
            Result.Success(it)
        }
    }

    override suspend fun getLeds(): Result<List<Led>> = withContext(ioDispatcher) {
        return@withContext try {
            Result.Success(ArrayList(deviceLeds.values))
        } catch (e: Exception) {
            Result.Error(java.lang.Exception("Communication failed"))
        }
    }

    override suspend fun updateLed(id: Int, isOn: Boolean) = withContext(ioDispatcher) {
        setLed(id, isOn)
    }

    private fun setLed(id: Int, isOn: Boolean) {
        val led = deviceLeds.get(id)
        led?.let {
            val newLed = it.copy(isOn = isOn)
            deviceLeds.set(id, newLed)
        }
    }

}