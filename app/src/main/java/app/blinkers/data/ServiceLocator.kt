package app.blinkers.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import app.blinkers.data.source.*
import app.blinkers.data.source.local.BlinkersDatabase
import kotlinx.coroutines.runBlocking

object ServiceLocator {

    private val lock = Any()
    private var database: BlinkersDatabase? = null

    @Volatile
    var blinkersRepository: BlinkersRepository? = null
        @VisibleForTesting set


    fun provideBlinkersRepository(context: Context): BlinkersRepository {
        synchronized(this) {
            return blinkersRepository ?: blinkersRepository ?: createBlinkersRepository(context)
        }
    }

    private fun createBlinkersRepository(context: Context): BlinkersRepository {
        val newRepo = DefaultBlinkersRepository(
            DefaultDeviceCommunicator,
            createBlinkersLocalDataSource(context)
        )
        blinkersRepository = newRepo
        return newRepo
    }

    private fun createBlinkersLocalDataSource(context: Context): BlinkersDataSource {
        val database = database ?: createDataBase(context)
        return BlinkersLocalDataSource(database.blinkersDao())
    }

    private fun createDataBase(context: Context): BlinkersDatabase {
        val result = Room.databaseBuilder(
            context.applicationContext,
            BlinkersDatabase::class.java, "Blinkers.db"
        ).build()
        database = result
        return result
    }

    @VisibleForTesting
    fun resetRepository() {
        synchronized(lock) {
            runBlocking {
             // TODO
                //   BlinkersLiveDataSource.deleteAllTasks()
            }
            // Clear all data to avoid test pollution.
            database?.apply {
                clearAllTables()
                close()
            }
            database = null
            blinkersRepository = null
        }
    }
}
