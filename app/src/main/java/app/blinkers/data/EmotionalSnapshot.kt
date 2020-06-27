package app.blinkers.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "EmotionalSnapshot")
data class EmotionalSnapshot(
    @PrimaryKey @ColumnInfo(name = "timestamp") var timestamp: Long = 0,
    @ColumnInfo(name = "valence") val valence: Int,
    @ColumnInfo(name = "arousal") val arousal: Int,
    @ColumnInfo(name = "dominance") val dominance: Int)