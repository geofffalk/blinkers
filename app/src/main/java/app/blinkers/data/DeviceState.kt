package app.blinkers.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DeviceState")
data class DeviceState @JvmOverloads constructor(
    @PrimaryKey @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "ledStatus") var ledStatus: Int = -1,
    @Embedded val eegSnapshot: EEGSnapshot? = null) {
}

data class EEGSnapshot(
    @ColumnInfo(name = "signalStrength") val signalStrength: Int,
    @ColumnInfo(name = "delta") val delta: Int,
    @ColumnInfo(name = "theta") val theta: Int,
    @ColumnInfo(name = "lowAlpha") val lowAlpha: Int,
    @ColumnInfo(name = "highAlpha") val highAlpha: Int,
    @ColumnInfo(name = "lowBeta") val lowBeta: Int,
    @ColumnInfo(name = "highBeta") val highBeta: Int,
    @ColumnInfo(name = "lowGamma") val lowGamma: Int,
    @ColumnInfo(name = "highGamma") val highGamma: Int)

