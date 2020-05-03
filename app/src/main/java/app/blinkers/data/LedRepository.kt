package app.blinkers.data

import androidx.lifecycle.LiveData

interface LedRepository {

    fun observeLeds(): LiveData<Result<List<Led>>>

    suspend fun getLeds(): Result<List<Led>>

    suspend fun setLed(led: Led, isOn: Boolean)
}