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

class RoomMigration2To3Test {

    @Test
    fun migration2To3_executesOnRealSqlite_addsColumns_andPreservesExistingRows() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // 1. Create Version 2 schema for download_tasks (including etag and last_modified from v1->v2)
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
                completed_at INTEGER,
                etag TEXT,
                last_modified TEXT
            )
        """.trimIndent())

        // 2. Insert pre-existing row in Version 2 table
        statement.execute("""
            INSERT INTO download_tasks (
                id, captured_media_id, title, source_domain, source_url, status, status_text,
                progress, downloaded_size_text, total_size_text, speed_text, eta_text,
                resolution_badge, codec_badge, local_path, created_at, completed_at,
                etag, last_modified
            ) VALUES (
                'task_v2_001', 'cap_002', 'Existing V2 Video', 'example.com', 'https://example.com/v2.mp4',
                'DOWNLOADING', 'Downloading', 0.65, '65 MB', '100 MB', '4.2 MB/s', '00:08',
                '1080p', 'H.264', '/sdcard/v2.mp4', 1700000000000, NULL,
                '"etag123"', 'Wed, 21 Oct 2025 07:28:00 GMT'
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

        // 4. Verify migration versions and run Migration 2 -> 3
        assertEquals(2, DatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, DatabaseMigrations.MIGRATION_2_3.endVersion)
        DatabaseMigrations.MIGRATION_2_3.migrate(proxyDb)

        // 5. Query table schema columns using PRAGMA table_info
        val columnsRs = statement.executeQuery("PRAGMA table_info(download_tasks)")
        val columnNames = mutableListOf<String>()
        while (columnsRs.next()) {
            columnNames.add(columnsRs.getString("name"))
        }
        columnsRs.close()

        assertTrue("retry_count column should exist in v3 schema", columnNames.contains("retry_count"))
        assertTrue("next_retry_at column should exist in v3 schema", columnNames.contains("next_retry_at"))

        // 6. Query and assert that the pre-existing row remains completely intact
        val queryRs = statement.executeQuery(
            "SELECT id, title, status, progress, etag, last_modified, retry_count, next_retry_at " +
            "FROM download_tasks WHERE id = 'task_v2_001'"
        )
        assertTrue("Pre-existing row must still exist after migration", queryRs.next())
        assertEquals("task_v2_001", queryRs.getString("id"))
        assertEquals("Existing V2 Video", queryRs.getString("title"))
        assertEquals("DOWNLOADING", queryRs.getString("status"))
        assertEquals(0.65f, queryRs.getFloat("progress"), 0.001f)
        assertEquals("\"etag123\"", queryRs.getString("etag"))
        assertEquals("Wed, 21 Oct 2025 07:28:00 GMT", queryRs.getString("last_modified"))

        // 7. Verify existing row receives retry_count = 0 and next_retry_at = NULL
        assertEquals(0, queryRs.getInt("retry_count"))
        assertNull("next_retry_at must default to NULL on existing row", queryRs.getObject("next_retry_at"))
        queryRs.close()

        statement.close()
        connection.close()
    }
}
