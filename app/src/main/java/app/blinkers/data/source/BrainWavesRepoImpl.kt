package app.blinkers.data.source

import androidx.lifecycle.LiveData
import app.blinkers.data.BrainWaves
import app.blinkers.data.Result

class BrainWavesRepoImpl(
    private val brainWavesLiveDataSource: BrainWavesLiveDataSource
) : BrainWavesRepository {

    override fun observeBrainWaves() : LiveData<Result<BrainWaves>> = brainWavesLiveDataSource.observeBrainWaves()

}