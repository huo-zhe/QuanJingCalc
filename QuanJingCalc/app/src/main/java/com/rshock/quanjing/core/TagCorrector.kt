package com.rshock.quanjing.core

object TagCorrector {

    sealed class Result {
        /** 成功锁定表里某一行 */
        data class Success(val tags: List<String>) : Result()
        /** 失败（筛到 0 行 / 筛完还剩多行 / 全空） */
        data class Failed(val reason: String) : Result()
    }

    /**
     * 走表：按位置 1→5 逐位置筛，唯一锁定则成功
     * @param ocrAtPos 长度 6 的数组，index 1~5 对应位置 1~5 的 OCR 词（null 表示该位置未识别）
     */
    fun match(ocrAtPos: Array<String?>): Result {
        // 全空
        if ((1..5).all { ocrAtPos[it] == null }) {
            return Result.Failed("OCR 未读到任何词条")
        }

        var candidates = PropheticWords.words.filter { it.size == 5 }

        for (pos in 1..5) {
            val ocrTag = ocrAtPos[pos] ?: continue  // 该位置空 → 跳过

            val next = candidates.filter { row ->
                isSimilar(ocrTag, row[pos - 1])
            }

            if (next.isEmpty()) {
                return Result.Failed("位置 $pos 的 OCR 结果「$ocrTag」与表里任何行都不匹配")
            }

            candidates = next

            if (candidates.size == 1) {
                return Result.Success(candidates[0])
            }
        }

        // 筛完位置 1~5 后还剩多行 → 失败
        return Result.Failed("匹配 ${candidates.size} 行：${candidates.joinToString(" | ") { it.joinToString("/") }}")
    }

    /** 词条相似判定 */
    private fun isSimilar(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.contains(b) || b.contains(a)) return true
        return editDistance(a, b) <= 1
    }

    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
        }
        return dp[m][n]
    }
}