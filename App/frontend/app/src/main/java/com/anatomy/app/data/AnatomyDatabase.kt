package com.anatomy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * AnatomyDatabase — Room database singleton.
 *
 * Database is initially empty. Organ data is fetched from API via OrganService.
 * See [OrganService] for data synchronization with backend API.
 */
@Database(entities = [OrganEntity::class], version = 2, exportSchema = false)
abstract class AnatomyDatabase : RoomDatabase() {

    abstract fun organDao(): OrganDao

    companion object {
        @Volatile
        private var INSTANCE: AnatomyDatabase? = null

        fun getInstance(context: Context): AnatomyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnatomyDatabase::class.java,
                    "anatomy_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
