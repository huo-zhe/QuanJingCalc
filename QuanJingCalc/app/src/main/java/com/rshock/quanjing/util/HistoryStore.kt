package com.rshock.quanjing.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryStore {

    private const val FILE = "history.json"
    private const val MAX = 50

    data class Record(
        val time: String,
        val tags: String,
        val result: String
    )

    fun add(ctx: Context, tags: String, result: String) {
        try {
            val file = File(ctx.filesDir, FILE)
            val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
            val obj = JSONObject().apply {
                put("time", SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date()))
                put("tags", tags)
                put("result", result)
            }
            arr.put(obj)
            // 只保留最近 MAX 条
            while (arr.length() > MAX) arr.remove(0)
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun load(ctx: Context): List<Record> {
        return try {
            val file = File(ctx.filesDir, FILE)
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(
                    time = o.optString("time"),
                    tags = o.optString("tags"),
                    result = o.optString("result")
                )
            }.reversed()
        } catch (_: Exception) { emptyList() }
    }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE).delete() } catch (_: Exception) {}
    }
}