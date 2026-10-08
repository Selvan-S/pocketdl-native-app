package com.pocketdl.app.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Structured database migrations for PocketDL Room database.
 */
object DatabaseMigrations {

    /**
     * Placeholder migration 1 -> 2 establishing the version migration structure.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Future schema additions will be defined here incrementally.
        }
    }

    val ALL_MIGRATIONS = arrayOf<Migration>(
        MIGRATION_1_2
    )
}
