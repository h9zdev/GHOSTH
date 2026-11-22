package net.wigle.wigleandroid.intelligence.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FindingEntity::class], version = 1, exportSchema = false)
abstract class IntelligenceDatabase : RoomDatabase() {
    abstract fun findingDao(): FindingDao

    companion object {
        @Volatile
        private var INSTANCE: IntelligenceDatabase? = null

        fun getInstance(context: Context): IntelligenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IntelligenceDatabase::class.java,
                    "intelligence.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
