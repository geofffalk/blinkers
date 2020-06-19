package app.blinkers.data

import androidx.lifecycle.LiveData

interface IORepository {

    fun observe(): LiveData<Result<ByteArray>>
    suspend fun write(data: ByteArray)

}