package com.java.myapplication.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条🦌记录：发生时间、时长（分钟）、备注、网页链接。
 */
data class SessionRecord(
    val id: Long,
    val timestamp: Long,   // 记录发生时间（epoch millis）
    val durationMin: Int,  // 时长（分钟）
    val note: String = "", // 备注
    val url: String = ""   // 网页链接
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("ts", timestamp)
        put("dur", durationMin)
        put("note", note)
        put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject) = SessionRecord(
            id = o.optLong("id"),
            timestamp = o.optLong("ts"),
            durationMin = o.optInt("dur"),
            note = o.optString("note"),
            url = o.optString("url")
        )
    }
}

/**
 * 最小实现：用 SharedPreferences 存 JSON 数组，无需数据库依赖。
 */
class RecordStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 按时间倒序返回全部记录 */
    fun load(): List<SessionRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { SessionRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(record: SessionRecord) {
        save(load().toMutableList().apply { add(record) })
    }

    fun remove(id: Long) {
        save(load().filter { it.id != id })
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    /* ==================== 备份：导入 / 导出 ==================== */

    /** 导出为 JSON 字符串（含版本与导出时间，便于将来扩展） */
    fun exportJson(): String {
        val arr = JSONArray()
        load().forEach { arr.put(it.toJson()) }
        val obj = JSONObject().apply {
            put("app", "luleme")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("records", arr)
        }
        return obj.toString(2)
    }

    /**
     * 从 JSON 导入记录。
     * @param replace true=替换（清空现有记录）；false=合并（按 id 去重，冲突保留现有）
     * @return 实际导入的记录条数
     */
    fun importJson(json: String, replace: Boolean): Int {
        val incoming = parseRecords(json)
        if (incoming.isEmpty()) return 0
        val merged = if (replace) {
            incoming
        } else {
            val existing = load()
            val ids = existing.mapTo(mutableSetOf()) { it.id }
            (existing + incoming.filter { ids.add(it.id) })
        }
        save(merged.sortedByDescending { it.timestamp })
        return incoming.size
    }

    /** 解析备份 JSON，兼容新格式 {records:[...]} 与旧纯数组格式 */
    private fun parseRecords(json: String): List<SessionRecord> {
        try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("records")
            if (arr != null) {
                return (0 until arr.length()).map { SessionRecord.fromJson(arr.getJSONObject(it)) }
            }
        } catch (_: Exception) {
            // 尝试纯数组格式
        }
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { SessionRecord.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<SessionRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "luleme"
        private const val KEY_RECORDS = "records"
    }
}