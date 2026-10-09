package com.pocketdl.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketdl.app.data.database.migrations.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.DriverManager

class RoomMigration1To2Test {

    @Test
    fun migration1To2_executesOnRealSqlite_addsColumns_andPreservesExistingRows() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // 1. Create Version 1 schema for download_tasks
        statement.execute("""
            CREATE TABLE download_tasks (
                id TEXT NOT NULL PRIMARY KEY,
                captured_media_id TEXT,
                title TEXT NOT NULL,
                source_domain TEXT NOT NULL,
                source_url TEXT NOT NULL,
                status TEXT NOT NULL,
                status_text TEXT NOT NULL,
                progress REAL NOT NULL,
                downloaded_size_text TEXT NOT NULL,
                total_size_text TEXT NOT NULL,
                speed_text TEXT NOT NULL,
                eta_text TEXT NOT NULL,
                resolution_badge TEXT NOT NULL,
                codec_badge TEXT NOT NULL,
                local_path TEXT,
                created_at INTEGER NOT NULL,
                completed_at INTEGER
            )
        """.trimIndent())

        // 2. Insert pre-existing row in Version 1 table
        statement.execute("""
            INSERT INTO download_tasks (
                id, captured_media_id, title, source_domain, source_url, status, status_text,
                progress, downloaded_size_text, total_size_text, speed_text, eta_text,
                resolution_badge, codec_badge, local_path, created_at, completed_at
            ) VALUES (
                'task_v1_001', 'cap_001', 'Existing V1 Video', 'example.com', 'https://example.com/v.mp4',
                'DOWNLOADING', 'Downloading', 0.45, '45 MB', '100 MB', '5.5 MB/s', '00:10',
                '1080p', 'H.264', '/sdcard/test.mp4', 1700000000000, NULL
            )
        """.trimIndent())

        // 3. Wrap SQLite connection with SupportSQLiteDatabase proxy
        val handler = InvocationHandler { _, method: Method, args: Array<out Any>? ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                val sql = args[0] as String
                statement.execute(sql)
            }
            null
        }

        val proxyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase

        // 4. Run Migration 1 -> 2
        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
        DatabaseMigrations.MIGRATION_1_2.migrate(proxyDb)

        // 5. Query table schema columns using PRAGMA table_info
        val columnsRs = statement.executeQuery("PRAGMA table_info(download_tasks)")
        val columnNames = mutableListOf<String>()
        while (columnsRs.next()) {
            columnNames.add(columnsRs.getString("name"))
        }
        columnsRs.close()

        assertTrue("etag column should exist in v2 schema", columnNames.contains("etag"))
        assertTrue("last_modified column should exist in v2 schema", columnNames.contains("last_modified"))

        // 6. Query and assert that the pre-existing row remains completely intact
        val queryRs = statement.executeQuery("SELECT id, title, status, progress, etag, last_modified FROM download_tasks WHERE id = 'task_v1_001'")
        assertTrue("Pre-existing row must still exist after migration", queryRs.next())
        assertEquals("task_v1_001", queryRs.getString("id"))
        assertEquals("Existing V1 Video", queryRs.getString("title"))
        assertEquals("DOWNLOADING", queryRs.getString("status"))
        assertEquals(0.45f, queryRs.getFloat("progress"), 0.001f)
        assertNull("etag must default to NULL on existing row", queryRs.getString("etag"))
        assertNull("last_modified must default to NULL on existing row", queryRs.getString("last_modified"))
        queryRs.close()

        statement.close()
        connection.close()
    }
}
