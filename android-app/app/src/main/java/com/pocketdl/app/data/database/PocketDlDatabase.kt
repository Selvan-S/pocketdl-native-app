package com.pocketdl.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pocketdl.app.data.database.dao.CapturedMediaDao
import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.CapturedMediaEntity
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.database.entity.MediaVariantEntity
import com.pocketdl.app.data.database.migrations.DatabaseMigrations

/**
 * Main Room database definition for PocketDL.
 */
@Database(
    entities = [
        CapturedMediaEntity::class,
        MediaVariantEntity::class,
        DownloadTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PocketDlDatabase : RoomDatabase() {

    abstract fun capturedMediaDao(): CapturedMediaDao
    abstract fun downloadTaskDao(): DownloadTaskDao

    companion object {
        const val DATABASE_NAME = "pocketdl.db"

        @Volatile
        private var INSTANCE: PocketDlDatabase? = null

        fun getInstance(context: Context): PocketDlDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): PocketDlDatabase {
            return Room.databaseBuilder(
                context,
                PocketDlDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .fallbackToDestructiveMigration(false)
                .build()
        }
    }
}
