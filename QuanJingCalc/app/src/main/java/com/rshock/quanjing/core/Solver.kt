package com.rshock.quanjing.core

data class Suggestion(
    val tags: List<String>,
    val heroes: List<Hero>,
    val topLevel: Int,
    val hasLeader: Boolean,
    val score: Int
)

sealed class Advice {
    data class HasLeader(val all3StarCombos: List<Suggestion>) : Advice()
    data class NoLeader(val allCombos: List<Suggestion>) : Advice()
    object NoMatch : Advice()
}

object Solver {

    fun solve(availableTags: List<String>): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        val n = availableTags.size
        for (mask in 1 until (1 shl n)) {
            val chosen = mutableListOf<String>()
            for (i in 0 until n) if (mask and (1 shl i) != 0) chosen.add(availableTags[i])
            if (chosen.size > 3) continue

            val matched = HeroData.dataset.filter { Calculator.isMatch(it.tags, chosen) }
            if (matched.isEmpty()) continue

            val topLv = matched.maxOf { it.level }
            if (topLv >= 3 && !chosen.any { it == "领袖" || it == "領袖" }) continue

            val hasLdr = chosen.any { it == "领袖" || it == "領袖" }

            // ★ 分档
            val levels = matched.map { it.level }.toSet()
            val tier = when {
                levels == setOf(3) -> 6
                levels == setOf(2) -> 5
                levels.contains(2) -> 4
                else -> 3
            }

            val precision = (100 - matched.size).coerceAtLeast(0)
            val tagsScore = (100 - chosen.size).coerceAtLeast(0)
            // 菁英强制高优（仅次于领袖，领袖逻辑已经在外面拦截了）
            val hasElite = chosen.any { it == "菁英" || it == "精英" }
            val eliteScore = if (hasElite) 1_000_000 else 0

            // ★ 替换原来的 score 计算：
            val score = eliteScore + tier * 1000000 + precision * 1000 + tagsScore

            out.add(Suggestion(chosen, matched, topLv, hasLdr, score))
        }
        return out.sortedByDescending { it.score }
    }

    fun getAdvice(list: List<Suggestion>): Advice {
        if (list.isEmpty()) return Advice.NoMatch
        val leaderCombos = list.filter { it.hasLeader && it.topLevel >= 3 }
        if (leaderCombos.isNotEmpty()) return Advice.HasLeader(leaderCombos)
        return Advice.NoLeader(list)
    }
}