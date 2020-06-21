package app.blinkers.data

import androidx.lifecycle.LiveData

interface IORepository {

    fun observe(): LiveData<Result<BrainWaves>>
    suspend fun write(data: ByteArray)

}