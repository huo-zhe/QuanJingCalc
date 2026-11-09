package com.rshock.quanjing.core

data class CalcResult(
    val selectedTags: List<String>,
    val heroes: List<Hero>
)

object Calculator {

    /** 从 selectedTags 中任取 1~3 个，匹配所有 Hero，按推荐度排序 */
    fun search(selectedTags: List<String>): List<CalcResult> {
        val out = mutableListOf<CalcResult>()
        fun dfs(start: Int, cur: MutableList<String>) {
            if (cur.isNotEmpty() && cur.size <= 3) {
                val matched = HeroData.dataset.filter { isMatch(it.tags, cur) }
                if (matched.isNotEmpty()) out.add(CalcResult(cur.toList(), matched))
            }
            if (cur.size == 3) return
            for (i in start until selectedTags.size) {
                cur.add(selectedTags[i])
                dfs(i + 1, cur)
                cur.removeAt(cur.size - 1)
            }
        }
        dfs(0, mutableListOf())
        return out.sortedByDescending { score(it) }
    }

    /** 与 HTML 原版 countValue 保持一致 */
    private fun score(r: CalcResult): Int {
        val maxLv = r.heroes.maxOf { it.level }
        return maxLv + (100 - r.heroes.size) + (100 - r.selectedTags.size)
    }

    /** 含领袖才能匹配到 3 星角色 */
    fun isMatch(heroTags: List<String>, selected: List<String>): Boolean {
        if (selected.isEmpty()) return false
        val isLv3 = selected.any { it == "领袖" || it == "領袖" }
        if (!isLv3 && heroTags.any { it == "领袖" || it == "領袖" }) return false
        return selected.all { heroTags.contains(it) }
    }
}