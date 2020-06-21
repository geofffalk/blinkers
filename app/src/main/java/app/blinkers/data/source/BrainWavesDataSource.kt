package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BrainWaves
import app.blinkers.data.Result

interface BrainWavesDataSource {
    fun observeBrainWaves() : LiveData<Result<BrainWaves>>
}