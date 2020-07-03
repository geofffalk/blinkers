package app.blinkers.data.source.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.blinkers.data.DeviceState
import app.blinkers.data.Analysis
import app.blinkers.data.EmotionalSnapshot

@Dao
interface BlinkerDao {

    @Query("SELECT * FROM DeviceState ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestDeviceState(): LiveData<DeviceState>

    @Query("SELECT * FROM EmotionalSnapshot ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestEmotionalSnapshot(): LiveData<EmotionalSnapshot>

    @Query("SELECT * from DeviceState")
    suspend fun getDeviceStates(): List<DeviceState>

    @Query("SELECT * from DeviceState WHERE timestamp > :timestamp")
    suspend fun getDeviceStatesFrom(timestamp: Long): List<DeviceState>

    @Query("SELECT * from EmotionalSnapshot WHERE timestamp > :timestamp")
    suspend fun getEmotionalSnapshotsFrom(timestamp: Long): List<EmotionalSnapshot>

    @Query("SELECT * from Analysis WHERE timestamp > :timestamp")
    suspend fun getAnalysisFrom(timestamp: Long): List<Analysis>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceState(deviceState: DeviceState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotionalSnapshot(emotionalSnapshot: EmotionalSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: Analysis)

}
