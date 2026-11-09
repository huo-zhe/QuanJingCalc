package com.rshock.quanjing.auto

import android.graphics.Bitmap
import android.graphics.Rect
import com.rshock.quanjing.core.Advice
import com.rshock.quanjing.core.Solver
import com.rshock.quanjing.core.Suggestion
import com.rshock.quanjing.core.TagCorrector
import com.rshock.quanjing.util.ConfigStore
import com.rshock.quanjing.util.SettingsStore
import kotlinx.coroutines.delay

object AutoRunner {

    class LeaderFoundException : Exception()

    sealed class Report2 {
        data class Success(
            val tags: List<String>,
            val advice: Advice,
            val tagRects: Map<String, Rect>,
            val ocrAtPos: Array<String?>
        ) : Report2()
        data class Failed(
            val reason: String,
            val ocrAtPos: Array<String?>? = null
        ) : Report2()
    }

    suspend fun analyze(roiSet: ConfigStore.RoiSet): Report2 {
        val cap = ScreenCaptureService.instance ?: return Report2.Failed("截屏服务未启动")
        val bmp = cap.capture() ?: return Report2.Failed("截屏失败")
        return try {
            val full = roiSet.full ?: return Report2.Failed("未校准")
            // 这里调用无参方法
            val rects = full.toPixelRects()
            val ocrAtPos = InputTagReader.readAtPositions(bmp, rects)

            when (val r = TagCorrector.match(ocrAtPos)) {
                is TagCorrector.Result.Failed -> return Report2.Failed(r.reason, ocrAtPos)
                is TagCorrector.Result.Success -> {
                    val tags = r.tags
                    val tagRects = mutableMapOf<String, Rect>()
                    val centers = listOf(
                        Pair(full.tagX1, full.tagY1),
                        Pair(full.tagX2, full.tagY2),
                        Pair(full.tagX3, full.tagY3),
                        Pair(full.tagX4, full.tagY4),
                        Pair(full.tagX5, full.tagY5)
                    )
                    for (i in 1..5) {
                        val cx = centers[i - 1].first
                        val cy = centers[i - 1].second
                        tagRects[tags[i - 1]] = Rect(cx.toInt(), cy.toInt(), cx.toInt() + 1, cy.toInt() + 1)
                    }
                    val suggestions = Solver.solve(tags)
                    return Report2.Success(tags, Solver.getAdvice(suggestions), tagRects, ocrAtPos)
                }
            }
        } catch (e: Exception) {
            return Report2.Failed(e.message ?: "异常")
        } finally {
            bmp.recycle()
        }
    }

    suspend fun autoPick(suggestion: Suggestion, tagRects: Map<String, Rect>) {
        for (tag in suggestion.tags) {
            val r = tagRects[tag] ?: continue
            ClickAccessibilityService.instance?.tap(r.left.toFloat(), r.top.toFloat())
            delay(SettingsStore.delayTag.toLong())
        }
    }

    suspend fun runFullAuto(
        roiSet: ConfigStore.RoiSet,
        onProgress: (String) -> Unit,
        shouldStop: () -> Boolean,
        onLeaderFound: (Report2.Success) -> Unit
    ) {
        val full = roiSet.full ?: return
        val auto = roiSet.auto ?: return
        val clicker = ClickAccessibilityService.instance ?: return
        val colorCfg = ConfigStore.buildColorCfg(full, auto)

        onProgress("准备启动…")
        delay(1000)

        val ranks = listOf(auto.rank1(), auto.rank2(), auto.rank3(), auto.rank4())

        while (!shouldStop()) {
            for ((idx, rank) in ranks.withIndex()) {
                if (shouldStop()) return
                clicker.tap(rank.first, rank.second)

                val cancelCp = colorCfg.cancelPoint
                if (cancelCp != null) {
                    while (!shouldStop()) {
                        val entered = StateDetector.waitForColor(
                            x = cancelCp.x.toInt(), y = cancelCp.y.toInt(),
                            targetColor = cancelCp.color, timeoutMs = 1000, stateName = "进入"
                        )
                        if (entered) break
                    }
                }
                if (shouldStop()) return

                try {
                    val innerOk = runInnerLoop(roiSet, onProgress, shouldStop, onLeaderFound)
                    if (!innerOk && shouldStop()) return

                    if (cancelCp != null) {
                        while (!shouldStop()) {
                            val exited = StateDetector.waitForColorGoneStable(
                                x = cancelCp.x.toInt(), y = cancelCp.y.toInt(),
                                targetColor = cancelCp.color, missCount = 3, timeoutMs = 2000, stateName = "退出"
                            )
                            if (exited) break
                        }
                    }
                } catch (e: LeaderFoundException) {
                    onProgress("发现领袖！！！，已终止")
                    return
                }
                if (shouldStop()) return
            }

            for ((idx, rank) in ranks.withIndex()) {
                if (shouldStop()) return
                clicker.tap(rank.first, rank.second)

                val (cx, cy) = auto.confirm()
                while (!shouldStop()) {
                    val ready = StateDetector.waitForColor(cx.toInt(), cy.toInt(), ConfigStore.COLOR_CONFIRM, 1000, "确定")
                    if (ready) break
                }
                if (shouldStop()) return
                delay(150)
                clicker.tap(cx, cy)

                while (!shouldStop()) {
                    val ok = StateDetector.waitForColor(rank.first.toInt(), rank.second.toInt(), 0xFF2EBAF8.toInt(), 1000, "蓝条")
                    if (ok) break
                }
                if (shouldStop()) return
            }

            if (shouldStop()) return
            val (rx, ry) = auto.recruit()
            clicker.tap(rx, ry)

            while (!shouldStop()) {
                val isBlack = StateDetector.waitForColor(rx.toInt(), ry.toInt(), 0xFF000000.toInt(), 1000, "黑屏")
                if (isBlack) break
            }
            if (shouldStop()) return

            colorCfg.skipDone?.let { cp ->
                while (!shouldStop()) {
                    val ready = StateDetector.waitForColorStable(cp.x.toInt(), cp.y.toInt(), cp.color, 3, 1000, "SKIP")
                    if (ready) {
                        clicker.tap(cp.x, cp.y)
                        delay(100)
                        clicker.tap(cp.x, cp.y)
                        break
                    }
                }
            }
            if (shouldStop()) return

            var closeOk = false
            var retry = 0
            val (clx, cly) = auto.close()
            while (!shouldStop() && !closeOk && retry < 2) {
                clicker.tap(clx, cly)
                closeOk = colorCfg.closeDone?.let { cp ->
                    StateDetector.waitForColorStable(cp.x.toInt(), cp.y.toInt(), cp.color, 3, 1500, "关闭")
                } ?: true
                if (!closeOk) { retry++; delay(300) }
            }
            if (!shouldStop()) delay(1000)
        }
    }

    private suspend fun runInnerLoop(
        roiSet: ConfigStore.RoiSet,
        onProgress: (String) -> Unit,
        shouldStop: () -> Boolean,
        onLeaderFound: (Report2.Success) -> Unit
    ): Boolean {
        val full = roiSet.full ?: return false
        val clicker = ClickAccessibilityService.instance ?: return false

        onProgress("点时间")
        val (tx, ty) = full.timePoint()
        clicker.tap(tx, ty)

        onProgress("识别中")
        val report = analyze(roiSet)
        if (report !is Report2.Success) return false

        when (val a = report.advice) {
            is Advice.NoMatch -> return false
            is Advice.NoLeader -> {
                val best = a.allCombos.firstOrNull() ?: return false
                autoPick(best, report.tagRects)
                delay(SettingsStore.delayStart.toLong())
                onProgress("点开始招募")
                val (sx, sy) = full.startPoint()
                clicker.tap(sx, sy)
                return true
            }
            is Advice.HasLeader -> {
                onLeaderFound(report)
                throw LeaderFoundException()
            }
        }
    }
}