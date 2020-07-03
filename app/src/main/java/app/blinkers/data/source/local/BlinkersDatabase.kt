package app.blinkers.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.blinkers.data.DeviceState
import app.blinkers.data.Analysis
import app.blinkers.data.EmotionalSnapshot

@Database(entities = [DeviceState::class, EmotionalSnapshot::class, Analysis::class], version = 1, exportSchema = false)
abstract class BlinkersDatabase: RoomDatabase() {

    abstract fun blinkersDao(): BlinkerDao
}