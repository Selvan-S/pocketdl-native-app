package com.pocketdl.app.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Structured database migrations for PocketDL Room database.
 *
 * Database versions:
 * - Version 1: Initial schema (captured_media, media_variants, download_tasks)
 * - Version 2: Added etag and last_modified columns to download_tasks
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN etag TEXT")
            db.execSQL("ALTER TABLE download_tasks ADD COLUMN last_modified TEXT")
        }
    }

    /**
     * All registered migrations for schema upgrades.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}
