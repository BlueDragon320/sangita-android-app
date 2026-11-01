package com.sangita.app.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.sangita.app.data.local.database.SangitaDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class Migration4To5Test {

    private lateinit var connection: Connection
    private lateinit var supportDb: SupportSQLiteDatabase

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        supportDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args[0] as String
                    connection.createStatement().use { it.execute(sql) }
                    null
                }
                else -> throw UnsupportedOperationException("Method ${method.name} not implemented")
            }
        } as SupportSQLiteDatabase
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun `migration from v4 to v5 adds failure tracking columns to sync_queue and preserves existing queue items`() {
        // 1. Create Version 4 sync_queue table
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    account_key TEXT NOT NULL DEFAULT 'default_account',
                    action_type TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    retry_count INTEGER NOT NULL,
                    last_error TEXT
                )
            """.trimIndent())

            // Insert pre-existing v4 queued action
            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count, last_error)
                VALUES ('user_123', 'FAVORITE_TRACK', '{"track_id":"tr-99"}', 5000, 1, 'Network timeout')
            """.trimIndent())
        }

        // 2. Run MIGRATION_4_5
        SangitaDatabase.MIGRATION_4_5.migrate(supportDb)

        // 3. Verify columns is_failed, failure_reason, failed_at exist and defaults match
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, account_key, action_type, payload_json, is_failed, failure_reason, failed_at FROM sync_queue WHERE id = 1").use { rs ->
                assertTrue("Existing queued action must be preserved", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("user_123", rs.getString("account_key"))
                assertEquals("FAVORITE_TRACK", rs.getString("action_type"))
                assertEquals("{\"track_id\":\"tr-99\"}", rs.getString("payload_json"))
                assertEquals(0, rs.getInt("is_failed"))
                assertNull(rs.getString("failure_reason"))
                assertEquals(0L, rs.getLong("failed_at"))
            }

            // Verify a new record with failed status can be inserted and queried
            stmt.execute("""
                INSERT INTO sync_queue (account_key, action_type, payload_json, created_at, retry_count, is_failed, failure_reason, failed_at)
                VALUES ('user_123', 'DELETE_PLAYLIST', '{"playlist_id":"pl-dead"}', 6000, 5, 1, 'HTTP 404', 6500)
            """.trimIndent())

            stmt.executeQuery("SELECT is_failed, failure_reason, failed_at FROM sync_queue WHERE id = 2").use { rs ->
                assertTrue(rs.next())
                assertEquals(1, rs.getInt("is_failed"))
                assertEquals("HTTP 404", rs.getString("failure_reason"))
                assertEquals(6500L, rs.getLong("failed_at"))
            }
        }
    }
}
