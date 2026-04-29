package com.riftwalker.aidebug.runtime.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.riftwalker.aidebug.protocol.DataStorePrefsGetRequest
import com.riftwalker.aidebug.protocol.DataStorePrefsSetRequest
import com.riftwalker.aidebug.protocol.PrefsGetRequest
import com.riftwalker.aidebug.protocol.PrefsSetRequest
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.StorageRestoreRequest
import com.riftwalker.aidebug.protocol.StorageSnapshotRequest
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorageControllerTest {
    @Test
    fun sharedPreferencesSetAndCleanupRestoresPreviousValue() {
        val fixture = newFixture()
        val fileName = uniqueName("flags")

        fixture.controller.prefsSet(
            PrefsSetRequest(
                fileName = fileName,
                key = "newCheckout",
                value = JsonPrimitive(true),
                type = "boolean",
            ),
        )

        assertTrue(
            fixture.controller
                .prefsGet(PrefsGetRequest(fileName, "newCheckout"))
                .value
                ?.jsonPrimitive
                ?.boolean == true,
        )

        fixture.cleanup.cleanupAll()

        assertFalse(fixture.controller.prefsGet(PrefsGetRequest(fileName, "newCheckout")).exists)
    }

    @Test
    fun sqliteExecAndCleanupRestoresRoomStyleDatabaseFile() {
        val fixture = newFixture()
        val databaseName = "${uniqueName("sample")}.db"
        createUserDatabase(fixture.context, databaseName)

        fixture.controller.sqlExec(
            SqlExecRequest(
                databaseName = databaseName,
                sql = "UPDATE users SET vip_level = ? WHERE id = ?",
                args = listOf("expired", "current"),
            ),
        )

        assertEquals("expired", queryVipLevel(fixture.controller, databaseName))

        fixture.cleanup.cleanupAll()

        assertEquals("active", queryVipLevel(fixture.controller, databaseName))
    }

    @Test
    fun dataStorePreferencesSetSnapshotAndRestore() {
        val fixture = newFixture()
        val name = uniqueName("settings")

        fixture.controller.dataStorePrefsSet(
            DataStorePrefsSetRequest(
                name = name,
                key = "newCheckout",
                value = JsonPrimitive(true),
                type = "boolean",
            ),
        )
        val snapshot = fixture.controller.snapshot(
            StorageSnapshotRequest(
                name = "before_datastore_change",
                dataStorePreferenceNames = listOf(name),
            ),
        )

        fixture.controller.dataStorePrefsSet(
            DataStorePrefsSetRequest(
                name = name,
                key = "newCheckout",
                value = JsonPrimitive(false),
                type = "boolean",
            ),
        )

        assertFalse(dataStoreFlag(fixture.controller, name))

        fixture.controller.restore(StorageRestoreRequest(snapshot.snapshotId))

        assertTrue(dataStoreFlag(fixture.controller, name))
    }

    private fun dataStoreFlag(controller: StorageController, name: String): Boolean {
        return controller
            .dataStorePrefsGet(DataStorePrefsGetRequest(name, "newCheckout"))
            .value
            ?.jsonPrimitive
            ?.boolean == true
    }

    private fun queryVipLevel(controller: StorageController, databaseName: String): String {
        val result = controller.sqlQuery(
            SqlQueryRequest(
                databaseName = databaseName,
                sql = "SELECT vip_level FROM users WHERE id = ?",
                args = listOf("current"),
            ),
        )
        return result.rows.single().getValue("vip_level")?.jsonPrimitive?.content.orEmpty()
    }

    private fun createUserDatabase(context: Context, databaseName: String) {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE users(id TEXT PRIMARY KEY, vip_level TEXT NOT NULL)")
            db.execSQL("INSERT INTO users(id, vip_level) VALUES(?, ?)", arrayOf("current", "active"))
        }
    }

    private fun newFixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val auditLog = AuditLog()
        val cleanup = CleanupRegistry(auditLog)
        return Fixture(
            context = context,
            cleanup = cleanup,
            controller = StorageController(context, auditLog, cleanup),
        )
    }

    private fun uniqueName(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
    }

    private data class Fixture(
        val context: Context,
        val cleanup: CleanupRegistry,
        val controller: StorageController,
    )
}
