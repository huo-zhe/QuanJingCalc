package com.rshock.quanjing.core

import android.content.Context
import org.json.JSONArray

data class Hero(
    val fullName: String,
    val tags: List<String>
) {
    val level: Int get() = fullName.firstOrNull()?.digitToIntOrNull() ?: 0
    val shortName: String get() = fullName.substringBefore('[').trim()
}

object HeroData {
    var dataset: List<Hero> = emptyList()
        private set

    fun load(ctx: Context) {
        dataset = JsonLoader.readHeroes(ctx)
    }
}

object JsonLoader {

    /** 从内部目录读 hero.json，不存在则从 assets 拷一份 */
    fun readHeroes(ctx: Context): List<Hero> {
        val json = readOrCopyAsset(ctx, "hero.json")
        val arr = JSONArray(json)
        val out = mutableListOf<Hero>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.getString("name")
            val tagsArr = obj.getJSONArray("tags")
            val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
            out.add(Hero(name, tags))
        }
        return out
    }

    /** 从内部目录读 prophecy.json，不存在则从 assets 拷一份 */
    fun readProphecy(ctx: Context): List<List<String>> {
        val json = readOrCopyAsset(ctx, "prophecy.json")
        val arr = JSONArray(json)
        val out = mutableListOf<List<String>>()
        for (i in 0 until arr.length()) {
            val rowArr = arr.getJSONArray(i)
            out.add((0 until rowArr.length()).map { rowArr.getString(it) })
        }
        return out
    }

    /** 读内部文件；不存在则从 assets 复制过来 */
    private fun readOrCopyAsset(ctx: Context, name: String): String {
        val file = java.io.File(ctx.filesDir, name)
        if (!file.exists()) {
            ctx.assets.open(name).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.readText()
    }

    /** 写入内部文件（用于导入） */
    fun writeFile(ctx: Context, name: String, content: String) {
        java.io.File(ctx.filesDir, name).writeText(content)
    }
}