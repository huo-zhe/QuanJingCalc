package com.rshock.quanjing.auto

import android.graphics.Bitmap
import android.graphics.Rect
import com.rshock.quanjing.util.ZhConverter

object InputTagReader {

    val TAG_KEYWORDS = listOf(
        "攻击者","守护者","辅助者","治疗者","妨碍者",
        "魔族","亚人","人类",
        "贫乳","美乳","巨乳",
        "小体型","中体型",
        "水属性","火属性","风属性","光属性","暗属性","闇属性",
        "回复","输出","削弱","干扰","保护","士兵","防御","支援",
        "爆发力","生存力","群体攻击","越战越强",
        "领袖","精英","菁英","回击"
    )

    /**
     * 对 5 个框分别 OCR（放大 2 倍提高识别率）
     * @param rects 5 个框（位置 1~5）
     * @return 长度 6 的数组，index 1~5 对应位置 1~5 的识别结果，null 表示未识别
     */
    suspend fun readAtPositions(fullFrame: Bitmap, rects: List<Rect>): Array<String?> {
        val result = arrayOfNulls<String>(6)

        val bmpW = fullFrame.width
        val bmpH = fullFrame.height

        for (i in rects.indices) {
            val r = rects[i]
            if (r.width() <= 0 || r.height() <= 0) continue

            // 边界裁剪
            val left = r.left.coerceIn(0, bmpW - 1)
            val top = r.top.coerceIn(0, bmpH - 1)
            val right = r.right.coerceIn(left + 1, bmpW)
            val bottom = r.bottom.coerceIn(top + 1, bmpH)

            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) continue

            val cropped = try {
                Bitmap.createBitmap(fullFrame, left, top, w, h)
            } catch (_: Exception) { continue }

            // 放大后 OCR
            val zoom = com.rshock.quanjing.util.SettingsStore.ocrZoom
            val scaled = Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * zoom).toInt(),
                (cropped.height * zoom).toInt(),
                true
            )
            cropped.recycle()

            val lines = OcrEngine.recognize(scaled)
            android.util.Log.d("QJ", "位置 ${i + 1} OCR原始：${lines.joinToString(" | ") { it.text }}")
            scaled.recycle()

            var best: String? = null
            for (line in lines) {
                val text = com.rshock.quanjing.util.ZhConverter.toSimplified(
                    line.text.trim().replace(" ", "").replace("|", "")
                )
                if (text.isEmpty()) continue

                // ★ 加编辑距离容错：找"最接近"的词条
                var bestKw: String? = null
                var bestDist = Int.MAX_VALUE
                for (kw in TAG_KEYWORDS) {
                    if (kw == text) { bestKw = kw; bestDist = 0; break }
                    if (kw.contains(text) || text.contains(kw)) {
                        val d = kotlin.math.abs(kw.length - text.length)
                        if (d < bestDist) { bestDist = d; bestKw = kw }
                        continue
                    }
                    val d = editDistance(kw, text)
                    if (d <= 2 && d < bestDist) { bestDist = d; bestKw = kw }
                }
                if (bestKw == null) continue
                val norm = normalize(bestKw)
                if (best == null || norm.length > best!!.length) best = norm
            }

            result[i + 1] = best
        }

        return result
    }

    /**
     * 只做 OCR，不做过滤
     * @return 长度 6 的数组（index 1~5），每个位置是原始 OCR 文字
     */
    suspend fun readRawAtPositions(fullFrame: Bitmap, rects: List<Rect>): Array<String?> {
        val result = arrayOfNulls<String>(6)
        val bmpW = fullFrame.width
        val bmpH = fullFrame.height

        for (i in rects.indices) {
            val r = rects[i]
            if (r.width() <= 0 || r.height() <= 0) continue

            val left = r.left.coerceIn(0, bmpW - 1)
            val top = r.top.coerceIn(0, bmpH - 1)
            val right = r.right.coerceIn(left + 1, bmpW)
            val bottom = r.bottom.coerceIn(top + 1, bmpH)

            val cropped = try {
                Bitmap.createBitmap(fullFrame, left, top, right - left, bottom - top)
            } catch (_: Exception) { continue }

            val zoom = com.rshock.quanjing.util.SettingsStore.ocrZoom
            val scaled = Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * zoom).toInt().coerceAtLeast(1),
                (cropped.height * zoom).toInt().coerceAtLeast(1),
                true
            )
            cropped.recycle()

            val lines = OcrEngine.recognize(scaled)
            scaled.recycle()

            // ★ 原始：所有识别到的文字拼起来
            val raw = lines.joinToString(" | ") { it.text.trim() }
            result[i + 1] = if (raw.isEmpty()) null else raw
        }

        return result
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

    fun normalize(t: String): String = when (t) {
        "暗属性" -> "闇属性"
        "精英" -> "菁英"
        else -> t
    }
}