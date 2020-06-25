package app.blinkers.data.source.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.blinkers.data.BlinkersState

@Database(entities = [BlinkersState::class], version = 1, exportSchema = false)
abstract class BlinkersDatabase: RoomDatabase() {

    abstract fun blinkersDao(): BlinkerDao
}