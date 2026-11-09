package com.rshock.quanjing.util

import android.content.Context
import android.graphics.Rect
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.qjDataStore by preferencesDataStore("qj_config")

object ConfigStore {

    private fun safeCtx(ctx: Context): Context = ctx.applicationContext

    data class RoiFull(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val x3: Float, val y3: Float, val x4: Float, val y4: Float,
        val timeX: Float, val timeY: Float, val startX: Float, val startY: Float,
        val tagX1: Float = 0f, val tagY1: Float = 0f, val tagX2: Float = 0f, val tagY2: Float = 0f,
        val tagX3: Float = 0f, val tagY3: Float = 0f, val tagX4: Float = 0f, val tagY4: Float = 0f,
        val tagX5: Float = 0f, val tagY5: Float = 0f
    ) {
        fun timePoint(): Pair<Float, Float> = timeX to timeY
        fun startPoint(): Pair<Float, Float> = startX to startY

        fun toPixelRects(): List<Rect> {
            val colW1 = x3 - x1; val colW2 = x4 - x3
            return listOf(
                Rect(x1.toInt(), y1.toInt(), (x1 + colW1).toInt(), y3.toInt()),
                Rect((x1 + colW1).toInt(), y1.toInt(), (x1 + colW1 + colW2).toInt(), y3.toInt()),
                Rect((x1 + colW1 + colW2).toInt(), y1.toInt(), x2.toInt(), y3.toInt()),
                Rect(x1.toInt(), y3.toInt(), (x1 + colW1).toInt(), y2.toInt()),
                Rect((x1 + colW1).toInt(), y3.toInt(), (x1 + colW1 + colW2).toInt(), y2.toInt())
            )
        }
    }

    data class RoiAuto(
        val rank1X: Float, val rank1Y: Float, val rank2X: Float, val rank2Y: Float,
        val rank3X: Float, val rank3Y: Float, val rank4X: Float, val rank4Y: Float,
        val confirmX: Float, val confirmY: Float, val recruitX: Float, val recruitY: Float,
        val skipX: Float, val skipY: Float, val closeX: Float, val closeY: Float
    ) {
        fun rank1() = rank1X to rank1Y
        fun rank2() = rank2X to rank2Y
        fun rank3() = rank3X to rank3Y
        fun rank4() = rank4X to rank4Y
        fun confirm() = confirmX to confirmY
        fun recruit() = recruitX to recruitY
        fun skip() = skipX to skipY
        fun close() = closeX to closeY
    }

    data class RoiSet(val full: RoiFull?, val auto: RoiAuto?) {
        val isFullComplete: Boolean get() = full != null
        val isAutoComplete: Boolean get() = auto != null
    }

    const val COLOR_URGENT = 0xFF4F4747.toInt()
    const val COLOR_CONFIRM = 0xFFA1C2FF.toInt()
    const val COLOR_RECRUIT_ON = 0xFFAFD0FF.toInt()
    const val COLOR_RECRUIT_OFF = 0xFF436BA4.toInt()
    const val COLOR_SKIP = 0xFFFFFFFF.toInt()
    const val COLOR_CLOSE = 0xFFACCDFE.toInt()

    data class ColorPoint(val x: Float, val y: Float, val color: Int, val notColor: Int? = null)

    data class ColorCfg(
        val urgentDone: ColorPoint?, val recruitDone: ColorPoint?,
        val skipDone: ColorPoint?, val closeDone: ColorPoint?, val cancelPoint: ColorPoint?
    )

    fun buildColorCfg(full: RoiFull, auto: RoiAuto): ColorCfg = ColorCfg(
        urgentDone = ColorPoint(auto.confirmX, auto.confirmY, COLOR_URGENT),
        recruitDone = ColorPoint(auto.recruitX, auto.recruitY, COLOR_RECRUIT_ON, COLOR_RECRUIT_OFF),
        skipDone = ColorPoint(auto.skipX, auto.skipY, COLOR_SKIP),
        closeDone = ColorPoint(auto.closeX, auto.closeY, COLOR_CLOSE),
        cancelPoint = ColorPoint(full.timeX, full.timeY, 0xFF2EBAF8.toInt())
    )

    // ================= 4段式自适应插值模型 =================

    private data class ResPoint(val w: Float, val h: Float, val full: RoiFull, val auto: RoiAuto)

    // P1: 模拟器 (720x1280)
    private val P1 = ResPoint(720f, 1280f, RoiFull(
        114.75f, 467.55f, 603.0f, 588.44f, 271.12f, 529.77f, 448.87f, 533.33f,
        181.12f, 343.11f, 567.0f, 1041.77f,
        192.93f, 498.66f, 360.0f, 498.66f, 525.93f, 498.66f, 192.93f, 559.11f, 360.0f, 559.11f
    ), RoiAuto(
        411.18f, 309.33f, 405.0f, 524.44f, 411.18f, 739.55f, 411.18f, 954.66f,
        550.12f, 782.22f, 428.06f, 1045.33f, 573.75f, 74.66f, 424.12f, 1201.77f
    ))

    // P2: 真机 B (1080x2160)
    private val P2 = ResPoint(1080f, 2160f, RoiFull(
        128.0f, 788.0f, 950.0f, 988.0f, 391.0f, 894.0f, 688.0f, 898.0f,
        246.0f, 572.0f, 875.0f, 1768.0f,
        259.5f, 841.0f, 539.5f, 841.0f, 819.0f, 841.0f, 259.5f, 941.0f, 539.5f, 941.0f
    ), RoiAuto(
        623.0f, 524.0f, 621.0f, 882.0f, 623.0f, 1240.0f, 623.0f, 1598.0f,
        871.0f, 1308.0f, 659.0f, 1758.0f, 905.0f, 128.0f, 639.0f, 2016.0f
    ))

    // P3: 模拟器 (1080x2340)
    private val P3 = ResPoint(1080f, 2340f, RoiFull(
        88.15f, 855.83f, 988.15f, 1076.83f, 384.0f, 968.50f, 696.0f, 968.50f,
        216.92f, 624.0f, 900.0f, 1902.33f,
        236.08f, 912.17f, 540.0f, 912.17f, 842.08f, 912.17f, 236.08f, 1022.67f, 540.0f, 1022.67f
    ), RoiAuto(
        634.15f, 563.33f, 630.0f, 951.17f, 634.15f, 1339.0f, 634.15f, 1726.83f,
        898.15f, 1438.67f, 660.0f, 1911.0f, 937.85f, 143.0f, 665.08f, 2199.17f
    ))

    // P4: 真机 A (1220x2712)
    private val P4 = ResPoint(1220f, 2712f, RoiFull(
        104.37f, 997.83f, 1112.44f, 1254.47f, 434.97f, 1126.47f, 780.70f, 1125.09f,
        324.48f, 749.73f, 1010.07f, 2198.94f,
        269.67f, 1062.15f, 607.84f, 1062.15f, 946.57f, 1062.15f, 269.67f, 1190.47f, 607.84f, 1190.47f
    ), RoiAuto(
        714.71f, 695.60f, 725.46f, 1122.22f, 714.71f, 1548.83f, 714.71f, 1975.45f,
        992.44f, 1650.58f, 761.33f, 2211.07f, 1038.19f, 193.74f, 737.21f, 2497.44f
    ))

    private fun interpolate(w: Float, h: Float): Pair<RoiFull, RoiAuto> {
        val points = listOf(P1, P2, P3, P4).sortedBy { it.h }
        var pA = points.first()
        var pB = points.last()
        for (i in 0 until points.size - 1) {
            if (h >= points[i].h && h <= points[i + 1].h) {
                pA = points[i]; pB = points[i + 1]; break
            }
        }
        // 超出最大范围或最小范围时，取两端的线做延伸
        if (h < points.first().h) { pA = points[0]; pB = points[1] }
        if (h > points.last().h) { pA = points[points.size - 2]; pB = points.last() }

        val ratioH = if (pB.h - pA.h == 0f) 0f else (h - pA.h) / (pB.h - pA.h)
        val ratioW = if (pB.w - pA.w == 0f) 0f else (w - pA.w) / (pB.w - pA.w)

        fun lerp(vA: Float, vB: Float, rH: Float, rW: Float): Float = vA + (vB - vA) * ((rH + rW) / 2f)

        val full = RoiFull(
            lerp(pA.full.x1, pB.full.x1, ratioH, ratioW), lerp(pA.full.y1, pB.full.y1, ratioH, ratioW),
            lerp(pA.full.x2, pB.full.x2, ratioH, ratioW), lerp(pA.full.y2, pB.full.y2, ratioH, ratioW),
            lerp(pA.full.x3, pB.full.x3, ratioH, ratioW), lerp(pA.full.y3, pB.full.y3, ratioH, ratioW),
            lerp(pA.full.x4, pB.full.x4, ratioH, ratioW), lerp(pA.full.y4, pB.full.y4, ratioH, ratioW),
            lerp(pA.full.timeX, pB.full.timeX, ratioH, ratioW), lerp(pA.full.timeY, pB.full.timeY, ratioH, ratioW),
            lerp(pA.full.startX, pB.full.startX, ratioH, ratioW), lerp(pA.full.startY, pB.full.startY, ratioH, ratioW),
            lerp(pA.full.tagX1, pB.full.tagX1, ratioH, ratioW), lerp(pA.full.tagY1, pB.full.tagY1, ratioH, ratioW),
            lerp(pA.full.tagX2, pB.full.tagX2, ratioH, ratioW), lerp(pA.full.tagY2, pB.full.tagY2, ratioH, ratioW),
            lerp(pA.full.tagX3, pB.full.tagX3, ratioH, ratioW), lerp(pA.full.tagY3, pB.full.tagY3, ratioH, ratioW),
            lerp(pA.full.tagX4, pB.full.tagX4, ratioH, ratioW), lerp(pA.full.tagY4, pB.full.tagY4, ratioH, ratioW),
            lerp(pA.full.tagX5, pB.full.tagX5, ratioH, ratioW), lerp(pA.full.tagY5, pB.full.tagY5, ratioH, ratioW)
        )
        val auto = RoiAuto(
            lerp(pA.auto.rank1X, pB.auto.rank1X, ratioH, ratioW), lerp(pA.auto.rank1Y, pB.auto.rank1Y, ratioH, ratioW),
            lerp(pA.auto.rank2X, pB.auto.rank2X, ratioH, ratioW), lerp(pA.auto.rank2Y, pB.auto.rank2Y, ratioH, ratioW),
            lerp(pA.auto.rank3X, pB.auto.rank3X, ratioH, ratioW), lerp(pA.auto.rank3Y, pB.auto.rank3Y, ratioH, ratioW),
            lerp(pA.auto.rank4X, pB.auto.rank4X, ratioH, ratioW), lerp(pA.auto.rank4Y, pB.auto.rank4Y, ratioH, ratioW),
            lerp(pA.auto.confirmX, pB.auto.confirmX, ratioH, ratioW), lerp(pA.auto.confirmY, pB.auto.confirmY, ratioH, ratioW),
            lerp(pA.auto.recruitX, pB.auto.recruitX, ratioH, ratioW), lerp(pA.auto.recruitY, pB.auto.recruitY, ratioH, ratioW),
            lerp(pA.auto.skipX, pB.auto.skipX, ratioH, ratioW), lerp(pA.auto.skipY, pB.auto.skipY, ratioH, ratioW),
            lerp(pA.auto.closeX, pB.auto.closeX, ratioH, ratioW), lerp(pA.auto.closeY, pB.auto.closeY, ratioH, ratioW)
        )
        return full to auto
    }

    // ================= 保存与读取 =================

    fun saveFull(
        ctx: Context,
        p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float, p4x: Float, p4y: Float,
        tx: Float, ty: Float, sx: Float, sy: Float,
        tag1x: Float, tag1y: Float, tag2x: Float, tag2y: Float, tag3x: Float, tag3y: Float,
        tag4x: Float, tag4y: Float, tag5x: Float, tag5y: Float
    ) = runBlocking {
        val c = safeCtx(ctx)
        c.qjDataStore.edit { p ->
            p[floatPreferencesKey("f1x")] = p1x; p[floatPreferencesKey("f1y")] = p1y
            p[floatPreferencesKey("f2x")] = p2x; p[floatPreferencesKey("f2y")] = p2y
            p[floatPreferencesKey("f3x")] = p3x; p[floatPreferencesKey("f3y")] = p3y
            p[floatPreferencesKey("f4x")] = p4x; p[floatPreferencesKey("f4y")] = p4y
            p[floatPreferencesKey("ftx")] = tx; p[floatPreferencesKey("fty")] = ty
            p[floatPreferencesKey("fsx")] = sx; p[floatPreferencesKey("fsy")] = sy
            p[floatPreferencesKey("tx1")] = tag1x; p[floatPreferencesKey("ty1")] = tag1y
            p[floatPreferencesKey("tx2")] = tag2x; p[floatPreferencesKey("ty2")] = tag2y
            p[floatPreferencesKey("tx3")] = tag3x; p[floatPreferencesKey("ty3")] = tag3y
            p[floatPreferencesKey("tx4")] = tag4x; p[floatPreferencesKey("ty4")] = tag4y
            p[floatPreferencesKey("tx5")] = tag5x; p[floatPreferencesKey("ty5")] = tag5y
        }
    }

    fun saveAuto(
        ctx: Context,
        r1x: Float, r1y: Float, r2x: Float, r2y: Float, r3x: Float, r3y: Float, r4x: Float, r4y: Float,
        cx: Float, cy: Float, rx: Float, ry: Float, skx: Float, sky: Float, clx: Float, cly: Float
    ) = runBlocking {
        val c = safeCtx(ctx)
        c.qjDataStore.edit { p ->
            p[floatPreferencesKey("a1x")] = r1x; p[floatPreferencesKey("a1y")] = r1y
            p[floatPreferencesKey("a2x")] = r2x; p[floatPreferencesKey("a2y")] = r2y
            p[floatPreferencesKey("a3x")] = r3x; p[floatPreferencesKey("a3y")] = r3y
            p[floatPreferencesKey("a4x")] = r4x; p[floatPreferencesKey("a4y")] = r4y
            p[floatPreferencesKey("acx")] = cx; p[floatPreferencesKey("acy")] = cy
            p[floatPreferencesKey("arx")] = rx; p[floatPreferencesKey("ary")] = ry
            p[floatPreferencesKey("asx")] = skx; p[floatPreferencesKey("asy")] = sky
            p[floatPreferencesKey("aclx")] = clx; p[floatPreferencesKey("acly")] = cly
        }
    }

    fun load(ctx: Context): RoiSet = runBlocking {
        val c = safeCtx(ctx)
        val p = c.qjDataStore.data.first()

        val wm = c.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()

        val (defFull, defAuto) = interpolate(screenW, screenH)

        val full = RoiFull(
            p[floatPreferencesKey("f1x")] ?: defFull.x1, p[floatPreferencesKey("f1y")] ?: defFull.y1,
            p[floatPreferencesKey("f2x")] ?: defFull.x2, p[floatPreferencesKey("f2y")] ?: defFull.y2,
            p[floatPreferencesKey("f3x")] ?: defFull.x3, p[floatPreferencesKey("f3y")] ?: defFull.y3,
            p[floatPreferencesKey("f4x")] ?: defFull.x4, p[floatPreferencesKey("f4y")] ?: defFull.y4,
            p[floatPreferencesKey("ftx")] ?: defFull.timeX, p[floatPreferencesKey("fty")] ?: defFull.timeY,
            p[floatPreferencesKey("fsx")] ?: defFull.startX, p[floatPreferencesKey("fsy")] ?: defFull.startY,
            p[floatPreferencesKey("tx1")] ?: defFull.tagX1, p[floatPreferencesKey("ty1")] ?: defFull.tagY1,
            p[floatPreferencesKey("tx2")] ?: defFull.tagX2, p[floatPreferencesKey("ty2")] ?: defFull.tagY2,
            p[floatPreferencesKey("tx3")] ?: defFull.tagX3, p[floatPreferencesKey("ty3")] ?: defFull.tagY3,
            p[floatPreferencesKey("tx4")] ?: defFull.tagX4, p[floatPreferencesKey("ty4")] ?: defFull.tagY4,
            p[floatPreferencesKey("tx5")] ?: defFull.tagX5, p[floatPreferencesKey("ty5")] ?: defFull.tagY5
        )

        val auto = RoiAuto(
            p[floatPreferencesKey("a1x")] ?: defAuto.rank1X, p[floatPreferencesKey("a1y")] ?: defAuto.rank1Y,
            p[floatPreferencesKey("a2x")] ?: defAuto.rank2X, p[floatPreferencesKey("a2y")] ?: defAuto.rank2Y,
            p[floatPreferencesKey("a3x")] ?: defAuto.rank3X, p[floatPreferencesKey("a3y")] ?: defAuto.rank3Y,
            p[floatPreferencesKey("a4x")] ?: defAuto.rank4X, p[floatPreferencesKey("a4y")] ?: defAuto.rank4Y,
            p[floatPreferencesKey("acx")] ?: defAuto.confirmX, p[floatPreferencesKey("acy")] ?: defAuto.confirmY,
            p[floatPreferencesKey("arx")] ?: defAuto.recruitX, p[floatPreferencesKey("ary")] ?: defAuto.recruitY,
            p[floatPreferencesKey("asx")] ?: defAuto.skipX, p[floatPreferencesKey("asy")] ?: defAuto.skipY,
            p[floatPreferencesKey("aclx")] ?: defAuto.closeX, p[floatPreferencesKey("acly")] ?: defAuto.closeY
        )

        RoiSet(full, auto)
    }

    fun clearAll(ctx: Context) = runBlocking {
        val c = safeCtx(ctx)
        c.qjDataStore.edit { it.clear() }
    }
}