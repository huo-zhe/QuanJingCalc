package com.rshock.quanjing.auto

import android.graphics.Bitmap
import kotlinx.coroutines.delay

object StateDetector {

    private fun isColorMatch(c: Int, target: Int, tol: Int = 50): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val tr = (target shr 16) and 0xFF; val tg = (target shr 8) and 0xFF; val tb = target and 0xFF
        return Math.abs(r - tr) <= tol && Math.abs(g - tg) <= tol && Math.abs(b - tb) <= tol
    }

    /** 在 (x,y) 等待颜色命中（百分比坐标由调用方换算成像素） */
    suspend fun waitForColor(
        x: Int, y: Int,
        targetColor: Int,
        timeoutMs: Long = 3000,
        stateName: String,
        notColor: Int? = null
    ): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val bmp = ScreenCaptureService.instance?.capture()
            if (bmp != null) {
                val px = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
                bmp.recycle()
                val isNot = notColor != null && isColorMatch(px, notColor)
                if (!isNot && isColorMatch(px, targetColor)) {
                    android.util.Log.d("QJ_State", "耗时 ${System.currentTimeMillis() - start}ms，到达: $stateName")
                    return true
                }
            }
            delay(100)
        }
        android.util.Log.w("QJ_State", "超时！没等到: $stateName")
        return false
    }


    /** 抓色：返回某坐标 ARGB，用于调试 */
    fun sampleColor(x: Int, y: Int): Int? {
        val bmp = ScreenCaptureService.instance?.capture() ?: return null
        return try {
            bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
        } finally { bmp.recycle() }
    }

    suspend fun waitForColorGoneStable(
        x: Int, y: Int,
        targetColor: Int,
        missCount: Int = 3,
        timeoutMs: Long = 1000,
        stateName: String
    ): Boolean {
        var miss = 0
        var firstMissAt = 0L   // 0 表示还没出现第一次 miss
        while (true) {
            val bmp = ScreenCaptureService.instance?.capture()
            if (bmp == null) {
                // 取帧失败不计入判定
                delay(100)
                continue
            }
            val px = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
            bmp.recycle()

            if (isColorMatch(px, targetColor)) {
                // 命中：重置
                miss = 0
                firstMissAt = 0L
            } else {
                if (firstMissAt == 0L) firstMissAt = System.currentTimeMillis()
                miss++
                if (miss >= missCount) {
                    android.util.Log.d("QJ_State",
                        "连续${missCount}次未命中（首次 miss 起 ${System.currentTimeMillis() - firstMissAt}ms），已离开: $stateName")
                    return true
                }
                if (System.currentTimeMillis() - firstMissAt > timeoutMs) {
                    android.util.Log.w("QJ_State", "首次 miss 后超时，仍停留: $stateName")
                    return false
                }
            }
            delay(100)
        }
    }
    /** 连续 hitCount 次命中目标颜色才返回 true（用于过滤动画瞬白） */
    suspend fun waitForColorStable(
        x: Int, y: Int,
        targetColor: Int,
        hitCount: Int = 3,
        timeoutMs: Long = 3000,
        stateName: String
    ): Boolean {
        var hit = 0
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val bmp = ScreenCaptureService.instance?.capture()
            if (bmp != null) {
                val px = bmp.getPixel(x.coerceIn(0, bmp.width - 1), y.coerceIn(0, bmp.height - 1))
                bmp.recycle()
                if (isColorMatch(px, targetColor)) {
                    hit++
                    if (hit >= hitCount) {
                        android.util.Log.d("QJ_State", "连续${hitCount}次命中，到达: $stateName")
                        return true
                    }
                } else {
                    hit = 0 // 一旦未命中就重置
                }
            }
            delay(100)
        }
        android.util.Log.w("QJ_State", "超时！未达成连续命中: $stateName")
        return false
    }

    /**
     * 判断矩形区域内是否包含目标颜色（用于检测词条是否变蓝）
     * @param threshold 容差
     * @param ratio 区域内目标颜色的像素占比达到多少算命中（0.1 即 10%）
     */
    suspend fun hasColorInRect(
        rect: android.graphics.Rect,
        targetColor: Int,
        threshold: Int = 50,
        ratio: Float = 0.1f
    ): Boolean {
        val bmp = ScreenCaptureService.instance?.capture() ?: return false
        return try {
            var matchCount = 0
            var totalCount = 0
            // 步长设为 2 或更大，可以加快扫描速度
            val step = 2
            for (y in rect.top until rect.bottom step step) {
                for (x in rect.left until rect.right step step) {
                    // 防止越界
                    val safeX = x.coerceIn(0, bmp.width - 1)
                    val safeY = y.coerceIn(0, bmp.height - 1)
                    val pixel = bmp.getPixel(safeX, safeY)
                    if (isColorMatch(pixel, targetColor, threshold)) {
                        matchCount++
                    }
                    totalCount++
                }
            }
            totalCount > 0 && (matchCount.toFloat() / totalCount) >= ratio
        } finally {
            bmp.recycle()
        }
    }

}