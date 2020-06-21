package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BrainWaves
import app.blinkers.data.Result

class BrainWavesRepoImpl(
    private val brainWavesDataSource: BrainWavesDataSource
) : BrainWavesRepository {

    override fun observe() : LiveData<Result<BrainWaves>> = brainWavesDataSource.observeBrainWaves()

}