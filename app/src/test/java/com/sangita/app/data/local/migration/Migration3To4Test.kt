package com.sangita.app.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import com.sangita.app.data.local.database.SangitaDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

class Migration3To4Test {

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
    fun `migration from v3 to v4 adds account_key to sync_queue and preserves existing actions`() {
        // 1. Create Version 3 sync_queue table without account_key
        connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    action_type TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    retry_count INTEGER NOT NULL,
                    last_error TEXT
                )
            """.trimIndent())

            // Insert pre-existing v3 action
            stmt.execute("""
                INSERT INTO sync_queue (action_type, payload_json, created_at, retry_count, last_error)
                VALUES ('CREATE_PLAYLIST', '{"title":"Legacy Mix"}', 1000, 0, NULL)
            """.trimIndent())
        }

        // 2. Run MIGRATION_3_4
        SangitaDatabase.MIGRATION_3_4.migrate(supportDb)

        // 3. Verify column account_key exists and defaulted to default_account
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT id, account_key, action_type, payload_json FROM sync_queue WHERE id = 1").use { rs ->
                assertTrue("Record must exist after migration", rs.next())
                assertEquals(1L, rs.getLong("id"))
                assertEquals("default_account", rs.getString("account_key"))
                assertEquals("CREATE_PLAYLIST", rs.getString("action_type"))
                assertEquals("{\"title\":\"Legacy Mix\"}", rs.getString("payload_json"))
            }

            // Verify index index_sync_queue_account_key was created
            stmt.executeQuery("PRAGMA index_list('sync_queue')").use { rs ->
                val indices = mutableListOf<String>()
                while (rs.next()) {
                    indices.add(rs.getString("name"))
                }
                assertTrue(indices.contains("index_sync_queue_account_key"))
            }
        }
    }
}
