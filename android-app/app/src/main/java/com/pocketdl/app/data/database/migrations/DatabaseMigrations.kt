package com.pocketdl.app.data.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Structured database migrations for PocketDL Room database.
 *
 * Current database version: 1.
 * When a future phase introduces schema changes:
 * 1. Increment [com.pocketdl.app.data.database.PocketDlDatabase] version.
 * 2. Define the corresponding [Migration] instance here.
 * 3. Add the migration to [ALL_MIGRATIONS].
 */
object DatabaseMigrations {

    /**
     * All registered migrations for schema upgrades.
     * Empty at initial schema version 1.
     */
    val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}
