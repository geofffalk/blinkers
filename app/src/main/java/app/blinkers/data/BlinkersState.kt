package app.blinkers.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "BlinkerState")
data class BlinkersState @JvmOverloads constructor(
    @PrimaryKey @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "ledStatus") var ledStatus: Int = -1,
    @ColumnInfo(name = "signalStrength") var signalStrength: Int = 0,
    @ColumnInfo(name = "delta") var delta: Int = 0,
    @ColumnInfo(name = "theta") var theta: Int = 0,
    @ColumnInfo(name = "lowAlpha") var lowAlpha: Int = 0,
    @ColumnInfo(name = "highAlpha") var highAlpha: Int = 0,
    @ColumnInfo(name = "lowBeta") var lowBeta: Int = 0,
    @ColumnInfo(name = "highBeta") var highBeta: Int = 0,
    @ColumnInfo(name = "lowGamma") var lowGamma: Int = 0,
    @ColumnInfo(name = "highGamma") var highGamma: Int = 0) {

    val brainWaves: BrainWaves
        get() = BrainWaves(signalStrength, delta, theta, lowAlpha, highAlpha, lowBeta, highBeta, lowGamma, highGamma)

}