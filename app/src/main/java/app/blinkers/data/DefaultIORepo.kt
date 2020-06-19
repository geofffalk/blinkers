package app.blinkers.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.util.*

class DefaultIORepo(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IORepository {


    override fun observe() : LiveData<Result<ByteArray>> =  liveData(ioDispatcher) {
            try {
                val buffer = ByteArray(1024)
                var len: Int
                while (true) {
                    len = inputStream.read(buffer)
                    val data = buffer.copyOf(len)
                    emit(Result.Success(data))
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