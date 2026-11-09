package com.rshock.quanjing.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build

object CalibrationPreview {

    /**
     * 显示校准预览：在截图上画出 5 个框
     * @param screenshot 当前屏幕截图
     * @param rects 5 个存储的 ROI
     * @param onClose 用户关闭预览后回调
     */
    fun show(ctx: Context, screenshot: Bitmap, rects: List<Rect>, onClose: () -> Unit) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val root = FrameLayout(ctx).apply {
            setBackgroundColor(0xE6000000.toInt())
        }

        // 自定义 View 绘制截图 + 5 个框
        val overlay = object : View(ctx) {
            val fillPaint = Paint().apply {
                color = 0x44FF0000
                style = Paint.Style.FILL
            }
            val strokePaint = Paint().apply {
                color = 0xFFFF0000.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 6f
                isAntiAlias = true
            }
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 60f
                setShadowLayer(10f, 0f, 0f, Color.BLACK)
                isAntiAlias = true
            }
            val labelBgPaint = Paint().apply {
                color = 0xCC000000.toInt()
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                // 画截图（铺满整个屏幕）
                canvas.drawBitmap(screenshot, null,
                    Rect(0, 0, width, height), null)

                // 画 5 个框
                rects.forEachIndexed { i, r ->
                    canvas.drawRect(r, fillPaint)
                    canvas.drawRect(r, strokePaint)

                    // 每个框左上角标号
                    val label = "${i + 1}"
                    val labelX = r.left + 10f
                    val labelY = r.top + 55f
                    canvas.drawRect(
                        r.left.toFloat(), r.top.toFloat(),
                        r.left + 80f, r.top + 80f,
                        labelBgPaint
                    )
                    canvas.drawText(label, labelX, labelY, textPaint)
                }

                // 顶部提示
                val hint = "校准框预览（红框 = 5 个 ROI）"
                canvas.drawRect(0f, 0f, width.toFloat(), 120f, labelBgPaint)
                canvas.drawText(hint, 30f, 80f, textPaint)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                // 点任意位置关闭
                if (event.action == MotionEvent.ACTION_UP) {
                    onClose()
                }
                return true
            }
        }

        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 底部关闭按钮
        val closeBtn = TextView(ctx).apply {
            text = "关闭预览"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC4A90D9.toInt())
            setPadding(40, 25, 40, 25)
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                onClose()
            }
        }
        val btnLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 100
        }
        root.addView(closeBtn, btnLp)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }
}