package app.blinkers.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.sqrt

@Entity(tableName = "DeviceState")
data class DeviceState @JvmOverloads constructor(
    @PrimaryKey @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "ledStatus") var ledStatus: Int = -1,
    @Embedded val eegSnapshot: EEGSnapshot? = null)

data class EEGSnapshot(
    @ColumnInfo(name = "signalStrength") val signalStrength: Int,
    @ColumnInfo(name = "delta") val delta: Int,
    @ColumnInfo(name = "theta") val theta: Int,
    @ColumnInfo(name = "lowAlpha") val lowAlpha: Int,
    @ColumnInfo(name = "highAlpha") val highAlpha: Int,
    @ColumnInfo(name = "lowBeta") val lowBeta: Int,
    @ColumnInfo(name = "highBeta") val highBeta: Int,
    @ColumnInfo(name = "lowGamma") val lowGamma: Int,
    @ColumnInfo(name = "highGamma") val highGamma: Int) {

    fun normalised(precision: Int): EEGSnapshot {
        val rawReadings = listOf(delta, theta, lowAlpha, highAlpha, lowBeta, highBeta, lowGamma, highGamma)
        val quotient = sqrt(rawReadings.map { it.toDouble() * it }.sum())
        val normalised = rawReadings.map { ((it / quotient) * precision).toInt() }
        return EEGSnapshot(signalStrength, normalised[0], normalised[1], normalised[2], normalised[3], normalised[4], normalised[5], normalised[6], normalised[7])
    }

    fun array(): IntArray = intArrayOf(delta, theta, lowAlpha, highAlpha, lowBeta, highBeta, lowGamma, highGamma)

}

