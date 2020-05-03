package app.blinkers.data

import androidx.lifecycle.LiveData

interface BlinkersRepository {

    fun observeLeds(): LiveData<Result<List<Led>>>

    suspend fun getLeds(): Result<List<Led>>

//    suspend fun getLed(id: Int): Result<Led>
//
//    suspend fun updateLed(id: Int, isOn: Boolean)
}