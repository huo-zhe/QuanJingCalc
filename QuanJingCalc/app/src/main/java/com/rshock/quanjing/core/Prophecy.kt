package com.rshock.quanjing.core

data class Hint(
    val green: List<String>,  // 点击后唯一确定分支
    val blue: List<String>    // 多个分支
)

object Prophecy {

    fun hint(selected: List<String>, freeMode: Boolean = false): Hint? {
        if (freeMode) return null
        if (selected.isEmpty() || selected.size > 4) return null

        val matches = PropheticWords.words.filter { w ->
            w.take(selected.size).zip(selected).all { (a, b) -> a.startsWith(b) }
        }
        if (matches.isEmpty()) return null

        val counts = mutableMapOf<String, Int>()
        matches.forEach { w ->
            w.getOrNull(selected.size)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return Hint(
            green = counts.filterValues { it == 1 }.keys.toList(),
            blue = counts.filterValues { it > 1 }.keys.toList()
        )
    }

    /** 若唯一匹配，返回剩余需补全的标签 */
    fun autoComplete(selected: List<String>): List<String>? {
        if (selected.isEmpty() || selected.size > 4) return null
        val matches = PropheticWords.words.filter { w ->
            w.take(selected.size).zip(selected).all { (a, b) -> a.startsWith(b) }
        }
        return if (matches.size == 1) matches[0].drop(selected.size) else null
    }
}