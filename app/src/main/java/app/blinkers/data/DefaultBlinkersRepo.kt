package app.blinkers.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random

class DefaultBlinkersRepo(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlinkersRepository {

    private val _leds = MutableLiveData<List<Led>>()
    val leds: LiveData<List<Led>> = _leds;
    val rdm = Random(0)

    init {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post(object : Runnable {
            override fun run() {
                val bool = rdm.nextBoolean()
                _leds.value = listOf(Led(0, bool), Led(1, bool));
                mainHandler.postDelayed(this, 3000)
            }

        })

    }
    override fun observeLeds(): LiveData<Result<List<Led>>> {
        return leds.map {
            Result.Success(it)
        }
    }

    override suspend fun getLeds(): Result<List<Led>> {
        val list = listOf(Led(0, false), Led(1, true));
        return Result.Success(list)
    }

//    override suspend fun getLed(id: Int): Result<Led> {
//
//    }
//
//    override suspend fun updateLed(id: Int, isOn: Boolean) {
//
//    }
}