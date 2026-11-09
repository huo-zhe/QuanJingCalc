package com.rshock.quanjing.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object OcrPreview {

    /**
     * 显示 OCR 视角（只显示 5 个小图 + OCR 结果）
     */
    fun show(
        ctx: Context,
        screenshot: Bitmap,
        rects: List<Rect>,
        ocrResults: List<String?>,
        onClose: () -> Unit
    ) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val smallBitmaps = mutableListOf<Bitmap>()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE000000.toInt())
            setPadding(30, 30, 30, 30)
        }

        root.addView(TextView(ctx).apply {
            text = "OCR 视角（5 个小图 + 结果）"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
            setPadding(0, 0, 0, 20)
        })

        val scroll = ScrollView(ctx)
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        rects.forEachIndexed { i, r ->
            // 安全裁剪
            val safeL = r.left.coerceIn(0, screenshot.width - 1)
            val safeT = r.top.coerceIn(0, screenshot.height - 1)
            val safeR = r.right.coerceIn(safeL + 1, screenshot.width)
            val safeB = r.bottom.coerceIn(safeT + 1, screenshot.height)

            val cropped = try {
                Bitmap.createBitmap(screenshot, safeL, safeT, safeR - safeL, safeB - safeT)
            } catch (_: Exception) { null }

            if (cropped != null) {
                // 放大 3 倍
                val scaled = Bitmap.createScaledBitmap(
                    cropped, cropped.width * 3, cropped.height * 3, true
                )
                cropped.recycle()
                smallBitmaps.add(scaled)

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 20, 0, 20)
                }

                // 序号
                row.addView(TextView(ctx).apply {
                    text = "${i + 1}"
                    setTextColor(0xFFFFD700.toInt()); textSize = 22f
                    setPadding(0, 0, 20, 0)
                })

                // 小图
                val iv = ImageView(ctx).apply {
                    setImageBitmap(scaled)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                val ivLp = LinearLayout.LayoutParams(400, 150)
                row.addView(iv, ivLp)

                // OCR 结果
                row.addView(TextView(ctx).apply {
                    val v = ocrResults.getOrNull(i)
                    text = " → ${v ?: "(未识别)"}"
                    setTextColor(if (v != null) 0xFF00FF00.toInt() else 0xFFFF8888.toInt())
                    textSize = 18f
                    setPadding(30, 0, 0, 0)
                })

                list.addView(row)
            }
        }

        scroll.addView(list)
        val scrollLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        root.addView(scroll, scrollLp)

        root.addView(Button(ctx).apply {
            text = "关闭"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                // ★ recycle 所有 bitmap
                try { if (!screenshot.isRecycled) screenshot.recycle() } catch (_: Exception) {}
                smallBitmaps.forEach {
                    try { if (!it.isRecycled) it.recycle() } catch (_: Exception) {}
                }
                onClose()
            }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }
    fun showFullImage(ctx: Context, screenshot: Bitmap, onClose: () -> Unit) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE000000.toInt())
            setPadding(30, 50, 30, 30)
        }

        // 显示真实分辨率
        root.addView(TextView(ctx).apply {
            text = "屏幕截图预览 (${screenshot.width} x ${screenshot.height})"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
            setPadding(0, 0, 0, 20)
        })

        // 显示图片
        val iv = ImageView(ctx).apply {
            setImageBitmap(screenshot)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(iv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 关闭按钮
        root.addView(Button(ctx).apply {
            text = "关闭"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                try { if (!screenshot.isRecycled) screenshot.recycle() } catch (_: Exception) {}
                onClose()
            }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }
}