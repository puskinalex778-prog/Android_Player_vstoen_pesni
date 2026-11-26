package com.etelevizor.iptv.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.etelevizor.iptv.model.EpgNowNext
import com.etelevizor.iptv.model.EpgProgram

class EpgDatabase(context: Context) : SQLiteOpenHelper(context, "epg_cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_programs (
                channel_key TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT,
                category TEXT,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_epg_channel_time ON epg_programs(channel_key, start_ms, end_ms)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS epg_channel_alias (
                alias_key TEXT PRIMARY KEY,
                channel_key TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS epg_programs")
        db.execSQL("DROP TABLE IF EXISTS epg_channel_alias")
        onCreate(db)
    }

    fun insertAliases(aliases: Map<String, String>) {
        if (aliases.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT OR REPLACE INTO epg_channel_alias(alias_key, channel_key) VALUES(?, ?)"
            )
            for ((alias, chKey) in aliases) {
                stmt.bindString(1, alias)
                stmt.bindString(2, chKey)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertProgramsBatch(programs: List<EpgProgram>) {
        if (programs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO epg_programs(channel_key, title, description, category, start_ms, end_ms) VALUES(?, ?, ?, ?, ?, ?)"
            )
            for (p in programs) {
                stmt.bindString(1, p.channelKey)
                stmt.bindString(2, p.title)
                stmt.bindString(3, p.description)
                stmt.bindString(4, p.category)
                stmt.bindLong(5, p.startTimeMs)
                stmt.bindLong(6, p.endTimeMs)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resolveChannelKeys(candidateKeys: List<String>): List<String> {
        if (candidateKeys.isEmpty()) return emptyList()
        val resolved = LinkedHashSet<String>()
        resolved.addAll(candidateKeys)
        val db = readableDatabase
        for (key in candidateKeys) {
            db.rawQuery(
                "SELECT channel_key FROM epg_channel_alias WHERE alias_key = ? LIMIT 1",
                arrayOf(key)
            ).use { c ->
                if (c.moveToFirst()) resolved.add(c.getString(0))
            }
        }
        return resolved.toList()
    }

    fun getNowNext(candidateKeys: List<String>, nowMs: Long = System.currentTimeMillis()): EpgNowNext {
        val keys = resolveChannelKeys(candidateKeys)
        if (keys.isEmpty()) return EpgNowNext()
        val db = readableDatabase
        val placeholders = keys.joinToString(",") { "?" }

        var current: EpgProgram? = null
        val argsNow = (keys + nowMs.toString() + nowMs.toString()).toTypedArray()
        db.rawQuery(
            """
            SELECT channel_key, title, description, category, start_ms, end_ms
            FROM epg_programs
            WHERE channel_key IN ($placeholders) AND start_ms <= ? AND end_ms > ?
            ORDER BY start_ms DESC LIMIT 1
            """.trimIndent(),
            argsNow
        ).use { c ->
            if (c.moveToFirst()) {
                current = EpgProgram(
                    channelKey = c.getString(0),
                    title = c.getString(1),
                    description = c.getString(2).orEmpty(),
                    category = c.getString(3).orEmpty(),
                    startTimeMs = c.getLong(4),
                    endTimeMs = c.getLong(5)
                )
            }
        }

        var next: EpgProgram? = null
        val nextAfter = current?.endTimeMs ?: nowMs
        val argsNext = (keys + nextAfter.toString()).toTypedArray()
        db.rawQuery(
            """
            SELECT channel_key, title, description, category, start_ms, end_ms
            FROM epg_programs
            WHERE channel_key IN ($placeholders) AND start_ms >= ?
            ORDER BY start_ms ASC LIMIT 1
            """.trimIndent(),
            argsNext
        ).use { c ->
            if (c.moveToFirst()) {
                next = EpgProgram(
                    channelKey = c.getString(0),
                    title = c.getString(1),
                    description = c.getString(2).orEmpty(),
                    category = c.getString(3).orEmpty(),
                    startTimeMs = c.getLong(4),
                    endTimeMs = c.getLong(5)
                )
            }
        }

        return EpgNowNext(current, next)
    }

    fun getSchedule(
        candidateKeys: List<String>,
        fromMs: Long,
        toMs: Long
    ): List<EpgProgram> {
        val keys = resolveChannelKeys(candidateKeys)
        if (keys.isEmpty()) return emptyList()
        val db = readableDatabase
        val placeholders = keys.joinToString(",") { "?" }
        val args = (keys + toMs.toString() + fromMs.toString()).toTypedArray()
        val result = mutableListOf<EpgProgram>()
        db.rawQuery(
            """
            SELECT channel_key, title, description, category, start_ms, end_ms
            FROM epg_programs
            WHERE channel_key IN ($placeholders) AND start_ms < ? AND end_ms > ?
            ORDER BY start_ms ASC
            """.trimIndent(),
            args
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    EpgProgram(
                        channelKey = c.getString(0),
                        title = c.getString(1),
                        description = c.getString(2).orEmpty(),
                        category = c.getString(3).orEmpty(),
                        startTimeMs = c.getLong(4),
                        endTimeMs = c.getLong(5)
                    )
                )
            }
        }
        return result
    }

    fun pruneOldPrograms(olderThanMs: Long) {
        writableDatabase.delete("epg_programs", "end_ms < ?", arrayOf(olderThanMs.toString()))
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete("epg_programs", null, null)
        db.delete("epg_channel_alias", null, null)
    }

    fun getProgramCount(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM epg_programs", null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }
}
