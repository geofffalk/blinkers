package app.blinkers.data.source.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.blinkers.data.BlinkersState

@Dao
interface BlinkerDao {

    @Query("SELECT * FROM BlinkerState ORDER BY timestamp DESC LIMIT 1")
    fun observeLastBlinkerState(): LiveData<BlinkersState>

    @Query("SELECT * from BlinkerState")
    suspend fun getBlinkerStates(): List<BlinkersState>

    @Query("SELECT * from BlinkerState WHERE timestamp > :timestamp")
    suspend fun getBlinkerStatesLaterThan(timestamp: Long): List<BlinkersState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlinkerState(blinkerState: BlinkersState)

    @Query("DELETE FROM BlinkerState")
    suspend fun deleteBlinkerStates()
}
