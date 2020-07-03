package app.blinkers.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Analysis")
data class Analysis(
    @PrimaryKey @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "valence") val valence: Int,
    @ColumnInfo(name = "arousal") val arousal: Int,
    @ColumnInfo(name = "dominance") val dominance: Int,
    @Embedded val eeg: EEGSnapshot) {

    fun toDoubleArray(): DoubleArray = doubleArrayOf(timestamp.toDouble(),
        valence.toDouble(),
        arousal.toDouble(),
        dominance.toDouble(),
        eeg.signalStrength.toDouble(),
        eeg.delta.toDouble(),
        eeg.theta.toDouble(),
        eeg.lowAlpha.toDouble(),
    eeg.highAlpha.toDouble(),
    eeg.lowBeta.toDouble(),
    eeg.highBeta.toDouble(),
    eeg.lowGamma.toDouble(),
    eeg.highGamma.toDouble())
}