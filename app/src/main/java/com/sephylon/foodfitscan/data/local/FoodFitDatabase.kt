package com.sephylon.foodfitscan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedProductEntity::class, ScanHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FoodFitDatabase : RoomDatabase() {
    abstract fun cachedProductDao(): CachedProductDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        const val DATABASE_NAME = "foodfit_database"

        @Volatile private var instance: FoodFitDatabase? = null

        fun getInstance(context: Context): FoodFitDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoodFitDatabase::class.java,
                    DATABASE_NAME,
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
