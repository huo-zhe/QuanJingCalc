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
                        tagRects[tags[i - 1]] =
                            Rect(cx.toInt(), cy.toInt(), cx.toInt() + 1, cy.toInt() + 1)
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
            val clickX = r.left.toFloat()
            val clickY = r.top.toFloat()

            ClickAccessibilityService.instance?.tap(clickX, clickY)
            kotlinx.coroutines.delay(SettingsStore.delayTag.toLong())
        }
    }

    // ================= 全自动功能 =================

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
        val BLUE = 0xFF2EBAF8.toInt() // 已完成/选中的蓝条色
        val START_COLOR = 0xFFA1C2FF.toInt() // 开始招募的颜色

        onProgress("准备启动…")
        delay(500)

        while (!shouldStop()) {
            // ============ 1. 预检：是否已处于招募条件界面？ ============
            val (startX, startY) = full.startPoint()
            val startRect = Rect(startX.toInt() - 50, startY.toInt() - 50, startX.toInt() + 50, startY.toInt() + 50)

            val inRecruitUI = StateDetector.hasColorInRect(startRect, START_COLOR, threshold = 60, ratio = 0.05f)

            if (inRecruitUI) {
                try {
                    runInnerLoop(roiSet, onProgress, shouldStop, onLeaderFound)
                } catch (e: LeaderFoundException) {
                    return
                }
            }

            // ============ 2. 智能外循环：点谁就检测谁 ============
            val ranks = listOf(auto.rank1(), auto.rank2(), auto.rank3(), auto.rank4())
            for ((idx, rank) in ranks.withIndex()) {
                if (shouldStop()) return

                val checkRect = Rect(
                    rank.first.toInt() - 50, rank.second.toInt() - 50,
                    rank.first.toInt() + 50, rank.second.toInt() + 50
                )

                val isCompleted = StateDetector.hasColorInRect(checkRect, BLUE, 60, 0.1f)
                val isRecruited = StateDetector.hasColorInRect(checkRect, ConfigStore.COLOR_URGENT, 40, 0.1f)

                if (isCompleted) {
                    continue
                } else if (isRecruited) {
                    continue
                } else {
                    clicker.tap(rank.first, rank.second)
                    // 等进入内循环界面（检测开始招募按钮出现）
                    while (!shouldStop()) {
                        val entered = StateDetector.hasColorInRect(startRect, START_COLOR, threshold = 60, ratio = 0.05f)
                        if (entered) break
                        delay(150)
                    }
                    if (shouldStop()) return

                    try {
                        runInnerLoop(roiSet, onProgress, shouldStop, onLeaderFound)

                        // 退出内循环界面（连续 3 次未命中开始招募）
                        var missCount = 0
                        while (!shouldStop() && missCount < 3) {
                            val stillThere = StateDetector.hasColorInRect(startRect, START_COLOR, 60, 0.05f)
                            if (stillThere) {
                                missCount = 0
                            } else {
                                missCount++
                            }
                            delay(150)
                        }
                    } catch (e: LeaderFoundException) {
                        onProgress("遇领袖，终止全自动")
                        return
                    }
                }
            }

            // ============ 3. 统一确定 & 一键录取 & 跳过 & 关闭 ============
            if (shouldStop()) return

            // ★ 统一处理前面跳过的“已招募”榜单
            for ((idx, rank) in ranks.withIndex()) {
                val checkRect = Rect(rank.first.toInt() - 50, rank.second.toInt() - 50, rank.first.toInt() + 50, rank.second.toInt() + 50)
                if (StateDetector.hasColorInRect(checkRect, ConfigStore.COLOR_URGENT, 40, 0.1f)) {
                    clicker.tap(rank.first, rank.second)

                    val (cx, cy) = auto.confirm()
                    val confirmRect = Rect(cx.toInt() - 50, cy.toInt() - 50, cx.toInt() + 50, cy.toInt() + 50)
                    while (!shouldStop()) {
                        if (StateDetector.hasColorInRect(confirmRect, ConfigStore.COLOR_CONFIRM, 60, 0.05f)) break
                        delay(100)
                    }
                    if (shouldStop()) return
                    delay(150)
                    clicker.tap(cx, cy)

                    // 死等该位置出现蓝条，代表传输完成再点下一个
                    while (!shouldStop()) {
                        val ok = StateDetector.hasColorInRect(checkRect, BLUE, 60, 0.05f)
                        if (ok) break
                        delay(150)
                    }
                    if (shouldStop()) return
                }
            }

            // 接下来点一键录取
            val (rx, ry) = auto.recruit()
            clicker.tap(rx, ry)

            // 等黑屏
            val recruitRect = Rect(rx.toInt() - 50, ry.toInt() - 50, rx.toInt() + 50, ry.toInt() + 50)
            while (!shouldStop()) {
                val isBlack = StateDetector.hasColorInRect(recruitRect, 0xFF000000.toInt(), 50, 0.3f)
                if (isBlack) break
                delay(150)
            }
            if (shouldStop()) return

            // SKIP
            colorCfg.skipDone?.let { cp ->
                val skipRect = Rect(cp.x.toInt() - 50, cp.y.toInt() - 50, cp.x.toInt() + 50, cp.y.toInt() + 50)
                while (!shouldStop()) {
                    val ready = StateDetector.hasColorInRect(skipRect, cp.color, 60, 0.05f)
                    if (ready) {
                        clicker.tap(cp.x, cp.y)
                        delay(100)
                        clicker.tap(cp.x, cp.y)
                        break
                    }
                    delay(150)
                }
            }
            if (shouldStop()) return

            // 关闭
            var closeOk = false
            var retry = 0
            val (clx, cly) = auto.close()
            while (!shouldStop() && !closeOk && retry < 2) {
                clicker.tap(clx, cly)
                closeOk = colorCfg.closeDone?.let { cp ->
                    StateDetector.waitForColorStable(cp.x.toInt(), cp.y.toInt(), cp.color, 3, 1500, "关闭")
                } ?: true
                if (!closeOk) {
                    retry++
                    delay(300)
                }
            }
            if (!shouldStop()) delay(1000)
        }
    }

    // ================= 内循环 (极速重试) =================
    private suspend fun runInnerLoop(
        roiSet: ConfigStore.RoiSet,
        onProgress: (String) -> Unit,
        shouldStop: () -> Boolean,
        onLeaderFound: (Report2.Success) -> Unit
    ): Boolean {
        val full = roiSet.full ?: return false
        val clicker = ClickAccessibilityService.instance ?: return false
        val BLUE = 0xFF2EBAF8.toInt()

        onProgress("点时间")
        delay(50)
        val (tx, ty) = full.timePoint()
        clicker.tap(tx, ty)

        var ocrReport: Report2.Success? = null
        var retryCount = 0

        // 核心循环：最多重试 3 次
        while (!shouldStop() && retryCount < 3) {
            if (ocrReport == null) {
                onProgress("识别中")
                val report = analyze(roiSet)
                if (report is Report2.Success) {
                    ocrReport = report
                } else {
                    onProgress("OCR 失败，重试")
                    retryCount++
                    delay(100)
                    continue
                }
            }

            val report = ocrReport!!
            val advice = report.advice
            if (advice is Advice.HasLeader) {
                onLeaderFound(report)
                throw LeaderFoundException()
            }
            if (advice is Advice.NoMatch) {
                onProgress("无匹配组合")
                return false
            }

            val best = (advice as Advice.NoLeader).allCombos.firstOrNull() ?: return false
            val tagsToPick = best.tags

            // 检查这 5 个框哪些变蓝了
            val pixelRects = full.toPixelRects()
            val blueStatus = BooleanArray(5)
            for (i in 0..4) {
                blueStatus[i] = StateDetector.hasColorInRect(pixelRects[i], BLUE, 60, 0.1f)
            }

            // 找出 OCR 结果中还没变蓝的词条
            val tagsToClickNow = tagsToPick.filterIndexed { _, tag ->
                val posIndex = report.tags.indexOf(tag)
                if (posIndex in 0..4) !blueStatus[posIndex] else true
            }

            if (tagsToClickNow.isEmpty()) {
                break // 都蓝了，去点开始招募
            }

            // 点击没变蓝的词条
            for (tag in tagsToClickNow) {
                val r = report.tagRects[tag] ?: continue
                clicker.tap(r.left.toFloat(), r.top.toFloat())
                delay(SettingsStore.delayTag.toLong())
            }
            delay(150)
        }

        if (shouldStop()) return false
        onProgress("点开始招募")
        val (sx, sy) = full.startPoint()
        delay(SettingsStore.delayStart.toLong())
        clicker.tap(sx, sy)

        return true
    }
}