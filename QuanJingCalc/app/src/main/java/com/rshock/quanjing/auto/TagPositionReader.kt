package com.rshock.quanjing.auto

import android.graphics.Bitmap
import android.graphics.Rect
import com.rshock.quanjing.util.ZhConverter

object TagPositionReader {

    /**
     * 返回「词条名 -> 屏幕坐标 Rect」，用于自动点击
     * 同一词条出现多次时取面积最大的（最清晰的按钮）
     */
    suspend fun read(bitmap: Bitmap, roi: Rect): Map<String, Rect> {
        val c = Bitmap.createBitmap(bitmap, roi.left, roi.top, roi.width(), roi.height())
        val lines = OcrEngine.recognize(c)
        c.recycle()

        val map = mutableMapOf<String, Rect>()
        for (line in lines) {
            val text = ZhConverter.toSimplified(line.text.trim().replace(" ", ""))
            val hit = InputTagReader.TAG_KEYWORDS.firstOrNull { it == text || text.contains(it) }
                ?: continue
            val norm = InputTagReader.normalize(hit)
            val box = line.boundingBox ?: continue
            val full = Rect(box).apply { offset(roi.left, roi.top) }

            val prev = map[norm]
            if (prev == null || full.width() * full.height() > prev.width() * prev.height()) {
                map[norm] = full
            }
        }
        return map
    }
}