package app.blinkers.data

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class DefaultLedRepo(
    private val btDataSource: BluetoothDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LedRepository {

    override fun observeLeds(): LiveData<Result<List<Led>>> {
        return btDataSource.observeLeds()
    }

    override suspend fun getLeds(): Result<List<Led>> {
        return btDataSource.getLeds()
    }

    override suspend fun setLed(id: Int, isOn: Boolean) {
        btDataSource.updateLed(id, isOn)
    }
}