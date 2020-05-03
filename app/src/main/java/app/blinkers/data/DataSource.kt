package app.blinkers.data

import androidx.lifecycle.LiveData

interface DataSource {

    fun observeLeds(): LiveData<Result<List<Led>>>

    suspend fun getLeds(): Result<List<Led>>

    suspend fun updateLed(id: Int, isOn: Boolean)
}