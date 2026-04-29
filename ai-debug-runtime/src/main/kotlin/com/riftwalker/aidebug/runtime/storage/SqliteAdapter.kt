package com.riftwalker.aidebug.runtime.storage

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.riftwalker.aidebug.protocol.SqlExecRequest
import com.riftwalker.aidebug.protocol.SqlExecResponse
import com.riftwalker.aidebug.protocol.SqlQueryRequest
import com.riftwalker.aidebug.protocol.SqlQueryResponse
import com.riftwalker.aidebug.runtime.AuditLog
import com.riftwalker.aidebug.runtime.CleanupRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class SqliteAdapter(
    private val context: Context,
    private val auditLog: AuditLog,
    private val cleanupRegistry: CleanupRegistry,
) {
    fun query(request: SqlQueryRequest): SqlQueryResponse {
        requireReadSql(request.sql)
        open(request.databaseName, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(request.sql, request.args.toTypedArray()).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = mutableListOf<Map<String, JsonElement?>>()
                val maxRows = request.limit.coerceIn(1, MAX_QUERY_ROWS)
                var truncated = false
                while (cursor.moveToNext()) {
                    if (rows.size >= maxRows) {
                        truncated = true
                        break
                    }
                    rows += columns.associateWith { column -> cursor.valueAt(cursor.getColumnIndexOrThrow(column)) }
                }
                auditLog.recordRead(
                    tool = "storage.sql.query",
                    target = request.databaseName,
                    status = "success",
                    argumentsSummary = request.sql.take(SQL_AUDIT_LIMIT),
                    resultSummary = "${rows.size} row(s)",
                )
                return SqlQueryResponse(request.databaseName, columns, rows, truncated)
            }
        }
    }

    fun exec(request: SqlExecRequest): SqlExecResponse {
        requireWriteSql(request.sql)
        val before = captureDatabaseFiles(request.databaseName)
        val restoreToken = cleanupRegistry.register("restore sqlite ${request.databaseName}") {
            restoreDatabaseFiles(request.databaseName, before)
        }
        open(request.databaseName, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(request.sql, request.args.toTypedArray())
        }
        auditLog.recordMutation(
            tool = "storage.sql.exec",
            target = request.databaseName,
            restoreToken = restoreToken,
            status = "success",
            argumentsSummary = request.sql.take(SQL_AUDIT_LIMIT),
        )
        return SqlExecResponse(request.databaseName, restoreToken)
    }

    fun captureDatabaseFiles(databaseName: String): DatabaseFileSnapshot {
        val main = databaseFile(databaseName)
        val wal = File(main.absolutePath + "-wal")
        val shm = File(main.absolutePath + "-shm")
        return DatabaseFileSnapshot(
            main = main.readIfExists(),
            wal = wal.readIfExists(),
            shm = shm.readIfExists(),
        )
    }

    fun restoreDatabaseFiles(databaseName: String, snapshot: DatabaseFileSnapshot) {
        val main = databaseFile(databaseName)
        main.parentFile?.mkdirs()
        File(main.absolutePath + "-wal").writeOrDelete(snapshot.wal)
        File(main.absolutePath + "-shm").writeOrDelete(snapshot.shm)
        main.writeOrDelete(snapshot.main)
    }

    private fun open(databaseName: String, flags: Int): SQLiteDatabase {
        val file = databaseFile(databaseName)
        require(file.exists()) { "Database does not exist: $databaseName" }
        return SQLiteDatabase.openDatabase(file.absolutePath, null, flags)
    }

    private fun databaseFile(databaseName: String): File {
        require(databaseName.matches(SAFE_NAME)) { "Invalid database name: $databaseName" }
        return context.getDatabasePath(databaseName)
    }

    private fun Cursor.valueAt(index: Int): JsonElement? {
        return when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
            Cursor.FIELD_TYPE_STRING -> JsonPrimitive(getString(index))
            Cursor.FIELD_TYPE_BLOB -> JsonPrimitive("<blob:${getBlob(index).size} bytes>")
            else -> JsonNull
        }
    }

    private fun requireReadSql(sql: String) {
        val normalized = sql.trimStart().lowercase()
        require(normalized.startsWith("select") || normalized.startsWith("pragma")) {
            "Only SELECT and PRAGMA are allowed for storage.sql.query"
        }
    }

    private fun requireWriteSql(sql: String) {
        val normalized = sql.trimStart().lowercase()
        require(!normalized.startsWith("select") && !normalized.startsWith("pragma")) {
            "Use storage.sql.query for SELECT/PRAGMA statements"
        }
    }

    data class DatabaseFileSnapshot(
        val main: ByteArray?,
        val wal: ByteArray?,
        val shm: ByteArray?,
    )

    private companion object {
        val SAFE_NAME = Regex("[A-Za-z0-9_.-]+")
        const val MAX_QUERY_ROWS = 1_000
        const val SQL_AUDIT_LIMIT = 240
    }
}

private fun File.readIfExists(): ByteArray? = if (exists()) readBytes() else null

private fun File.writeOrDelete(bytes: ByteArray?) {
    if (bytes == null) {
        delete()
    } else {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }
}
