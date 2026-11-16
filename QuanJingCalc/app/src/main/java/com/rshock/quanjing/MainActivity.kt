package com.rshock.quanjing

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.rshock.quanjing.auto.ClickAccessibilityService
import com.rshock.quanjing.auto.ScreenCaptureService
import com.rshock.quanjing.core.HeroData
import com.rshock.quanjing.core.PropheticWords
import com.rshock.quanjing.ui.FloatWindow
import com.rshock.quanjing.ui.HomeScreen
import com.rshock.quanjing.ui.SettingsScreen
import com.rshock.quanjing.util.ConfigStore
import com.rshock.quanjing.util.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK && res.data != null) {
                val i = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_CODE, res.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_DATA, res.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(i) else startService(i)
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1000)
                    afterProjectionReady()
                }
            } else {
                Toast.makeText(this, "未授权录屏", Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getSharedPreferences("qj_ball", Context.MODE_PRIVATE)
            .edit().putBoolean("showing", false).apply()
        SettingsStore.init(this)
        HeroData.load(this)
        PropheticWords.load(this)

        when {
            intent?.getBooleanExtra("open_settings", false) == true -> showSettings()
            intent?.getBooleanExtra("recalibrate_full", false) == true -> {
                showHome()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showCalibrateFull()
                }, 300)
                moveTaskToBack(true)
            }
            intent?.getBooleanExtra("recalibrate_auto", false) == true -> {
                showHome()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showCalibrateAuto()
                }, 300)
                moveTaskToBack(true)
            }
            else -> showHome()
        }
    }

    private fun showHome() {
        setContent {
            MaterialTheme {
                Surface {
                    HomeScreen(
                        onStartBall = { startBallFlow() },
                        onOpenSettings = { showSettings() }
                    )
                }
            }
        }
    }

    private fun showSettings() {
        setContent {
            MaterialTheme {
                Surface {
                    SettingsScreen(
                        onReCalibrateFull = {
                            showHome()
                            showCalibrateFull()
                        },
                        onReCalibrateAuto = {
                            showHome()
                            showCalibrateAuto()
                        },
                        onImportJson = { importJson() },
                        onExportJson = { exportJson() },
                        onShowHistory = { showHistory() },
                        onDone = { showHome() }
                    )
                }
            }
        }
    }

    private fun startBallFlow() {
        try { stopService(Intent(this, ScreenCaptureService::class.java)) } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
            return
        }

        if (ClickAccessibilityService.instance == null) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
            return
        }

        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (_: Exception) {}
    }

    private fun afterProjectionReady() {
        val cfg = ConfigStore.load(this)
        if (cfg.isFullComplete) {
            FloatWindow.get(this).show(cfg)
            finish()
        } else {
            showCalibrateFull()
        }
    }

    // ==================== 第 1 套校准（招募条件 6 点） ====================

    private fun showCalibrateFull() {
        try { FloatWindow.get(this).hide() } catch (_: Exception) {}
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val dp = resources.displayMetrics.density
        fun Int.dpToPx() = (this * dp).toInt()

        val points = mutableListOf<PointF>()
        val oldCfg = ConfigStore.load(this)
        val oldFull = oldCfg.full
        if (oldFull != null) {
            points.add(PointF(oldFull.x1, oldFull.y1))
            points.add(PointF(oldFull.x2, oldFull.y2))
            points.add(PointF(oldFull.x3, oldFull.y3))
            points.add(PointF(oldFull.x4, oldFull.y4))
            points.add(PointF(oldFull.timeX, oldFull.timeY))
            points.add(PointF(oldFull.startX, oldFull.startY))
        }

        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x88000000.toInt())
            setStroke(2, 0x88FFFFFF.toInt())
            cornerRadius = 30f
        }

        val confirmBtn = Button(this).apply {
            text = "确定"
            isEnabled = points.size == 6
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx())
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        val cancelBtn = Button(this).apply {
            text = "重来"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        // ★ 新增默认和退出
        val resetBtn = Button(this).apply {
            text = "默认"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        val exitBtn = Button(this).apply {
            text = "退出"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }

        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x00000000)
            setPadding(10, 10, 10, 10)
            addView(confirmBtn)
            addView(cancelBtn)
            addView(resetBtn)
            addView(exitBtn)
        }

        val overlay = object : View(this) {
            var draggingIdx = -1

            val dotPaint = Paint().apply { color = 0x8800FFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val dotStroke = Paint().apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dp2px(2f); isAntiAlias = true }
            val timePaint = Paint().apply { color = 0xFF44DDFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val startPaint = Paint().apply { color = 0xFFFF88AA.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val fill = Paint().apply { color = 0x3300FF00; style = Paint.Style.FILL }
            val stroke = Paint().apply { color = 0xFF00FF00.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f; isAntiAlias = true }
            val textPaint = Paint().apply { color = Color.YELLOW; textSize = 60f; setShadowLayer(8f, 0f, 0f, Color.BLACK); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }

            fun dp2px(v: Float) = v * resources.displayMetrics.density

            fun computeRects(): List<Rect> {
                if (points.size < 4) return emptyList()
                val x1 = points[0].x; val y1 = points[0].y
                val x2 = points[1].x; val y2 = points[1].y
                val x3 = points[2].x; val y3 = points[2].y
                val x4 = points[3].x
                val colW1 = x3 - x1; val colW2 = x4 - x3
                return listOf(
                    Rect(x1.toInt(), y1.toInt(), (x1 + colW1).toInt(), y3.toInt()),
                    Rect((x1 + colW1).toInt(), y1.toInt(), (x1 + colW1 + colW2).toInt(), y3.toInt()),
                    Rect((x1 + colW1 + colW2).toInt(), y1.toInt(), x2.toInt(), y3.toInt()),
                    Rect(x1.toInt(), y3.toInt(), (x1 + colW1).toInt(), y2.toInt()),
                    Rect((x1 + colW1).toInt(), y3.toInt(), (x1 + colW1 + colW2).toInt(), y2.toInt())
                )
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val headerH = height * 0.08f
                textPaint.textSize = width * 0.05f
                val title = when {
                    points.size < 4 -> "点 ${points.size + 1}/6：${when (points.size) {
                        0 -> "整个招募条件左上角"
                        1 -> "整个招募条件大框右下角"
                        2 -> "一二行和一二列的交点(田字中间)"
                        3 -> "二三列之间"
                        else -> ""
                    }}"
                    points.size == 4 -> "点第 5 个：时间按钮(蓝色)"
                    points.size == 5 -> "点第 6 个：开始招募按钮(蓝色)"
                    else -> "可拖动微调"
                }
                canvas.drawText(title, width * 0.03f, headerH * 0.65f, textPaint)

                if (points.size >= 4) {
                    val rects = computeRects()
                    rects.forEachIndexed { i, r ->
                        canvas.drawRect(r, fill)
                        canvas.drawRect(r, stroke)
                        val numPaint = Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = dp2px(40f); setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt()); isAntiAlias = true }
                        canvas.drawText("${i + 1}", r.left + 10f, r.top + 60f, numPaint)
                    }
                }

                points.take(4).forEachIndexed { i, p ->
                    canvas.drawCircle(p.x, p.y, dp2px(15f), dotPaint)
                    canvas.drawCircle(p.x, p.y, dp2px(15f), dotStroke)
                    val numPaint = Paint().apply { color = 0xFF000000.toInt(); textSize = dp2px(22f); isAntiAlias = true }
                    canvas.drawText("${i + 1}", p.x - 8f, p.y + 8f, numPaint)
                }

                if (points.size >= 5) {
                    val p = points[4]
                    canvas.drawCircle(p.x, p.y, dp2px(22f), timePaint)
                    canvas.drawCircle(p.x, p.y, dp2px(22f), dotStroke)
                    val t = Paint().apply { color = 0xFF000000.toInt(); textSize = dp2px(22f); isAntiAlias = true }
                    canvas.drawText("时", p.x - dp2px(11f), p.y + dp2px(8f), t)
                }

                if (points.size >= 6) {
                    val p = points[5]
                    canvas.drawCircle(p.x, p.y, dp2px(22f), startPaint)
                    canvas.drawCircle(p.x, p.y, dp2px(22f), dotStroke)
                    val t = Paint().apply { color = 0xFF000000.toInt(); textSize = dp2px(22f); isAntiAlias = true }
                    canvas.drawText("招", p.x - dp2px(11f), p.y + dp2px(8f), t)
                }

                if (points.size >= 4) {
                    val cx = (points[0].x + points[1].x) / 2f
                    val cy = (points[1].y) + dp2px(40f)
                    val centerFill = Paint().apply { color = 0xAAFF4081.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
                    canvas.drawCircle(cx, cy, dp2px(18f), centerFill)
                    canvas.drawCircle(cx, cy, dp2px(18f), dotStroke)
                    val t = Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = dp2px(14f); isAntiAlias = true; textAlign = Paint.Align.CENTER }
                    canvas.drawText("移", cx, cy + dp2px(5f), t)
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        var minDist = Float.MAX_VALUE; var hit = -1
                        points.forEachIndexed { i, p ->
                            val d = Math.hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
                            if (d < minDist && d < dp2px(25f)) { minDist = d; hit = i }
                        }

                        if (hit >= 0) {
                            draggingIdx = hit
                        } else if (points.size < 6) {
                            val bmp = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                            if (bmp != null) {
                                val px = bmp.getPixel(e.x.toInt().coerceIn(0, bmp.width - 1), e.y.toInt().coerceIn(0, bmp.height - 1))
                                bmp.recycle()
                                val targetColor = when (points.size) {
                                    4 -> 0xFF2EBAF8.toInt()
                                    5 -> 0xFFA1C2FF.toInt()
                                    else -> 0
                                }
                                if (targetColor != 0) {
                                    val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                                    val tr = (targetColor shr 16) and 0xFF; val tg = (targetColor shr 8) and 0xFF; val tb = targetColor and 0xFF
                                    if (Math.abs(r - tr) > 60 || Math.abs(g - tg) > 60 || Math.abs(b - tb) > 60) {
                                        Toast.makeText(context, "⚠️ 颜色不对，请确认点到按钮了", Toast.LENGTH_SHORT).show()
                                        return true
                                    }
                                }
                            }
                            points.add(PointF(e.x, e.y))
                            if (points.size == 6) confirmBtn.isEnabled = true
                            invalidate()
                        } else {
                            if (points.size >= 4) {
                                val cx = (points[0].x + points[1].x) / 2f
                                val cy = (points[1].y) + dp2px(40f)
                                val d = Math.hypot((e.x - cx).toDouble(), (e.y - cy).toDouble()).toFloat()
                                if (d < dp2px(25f)) draggingIdx = 99
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggingIdx in 0..3) {
                            points[draggingIdx].x = e.x
                            points[draggingIdx].y = e.y
                            invalidate()
                        } else if (draggingIdx == 99) {
                            val cx = (points[0].x + points[1].x) / 2f
                            val cy = (points[1].y) + dp2px(40f)
                            val dx = e.x - cx
                            val dy = e.y - cy
                            for (i in points.indices) {
                                points[i].x += dx
                                points[i].y += dy
                            }
                            invalidate()
                        } else if (draggingIdx >= 4) {
                            points[draggingIdx].x = e.x
                            points[draggingIdx].y = e.y
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        draggingIdx = -1
                        return true
                    }
                }
                return true
            }
        }

        confirmBtn.setOnClickListener {
            if (points.size < 6) {
                Toast.makeText(this, "请先点满 6 个点", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val x1 = points[0].x; val y1 = points[0].y
            val x2 = points[1].x; val y2 = points[1].y
            val x3 = points[2].x; val y3 = points[2].y
            val x4 = points[3].x
            val colW1 = x3 - x1; val colW2 = x4 - x3

            val tagCenters = listOf(
                PointF(x1 + colW1 / 2f, (y1 + y3) / 2f),
                PointF(x1 + colW1 + colW2 / 2f, (y1 + y3) / 2f),
                PointF(x1 + colW1 + colW2 + (x2 - (x1 + colW1 + colW2)) / 2f, (y1 + y3) / 2f),
                PointF(x1 + colW1 / 2f, (y3 + y2) / 2f),
                PointF(x1 + colW1 + colW2 / 2f, (y3 + y2) / 2f)
            )

            ConfigStore.saveFull(
                this, points[0].x, points[0].y, points[1].x, points[1].y,
                points[2].x, points[2].y, points[3].x, points[3].y,
                points[4].x, points[4].y, points[5].x, points[5].y,
                tagCenters[0].x, tagCenters[0].y, tagCenters[1].x, tagCenters[1].y, tagCenters[2].x, tagCenters[2].y,
                tagCenters[3].x, tagCenters[3].y, tagCenters[4].x, tagCenters[4].y
            )

            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            FloatWindow.get(this).rebuild(ConfigStore.load(this))
            finish()
        }

        cancelBtn.setOnClickListener {
            points.clear(); confirmBtn.isEnabled = false; overlay.invalidate()
        }

        resetBtn.setOnClickListener {
            val defFull = ConfigStore.getDefaultFull(screenW.toFloat(), screenH.toFloat())
            points.clear()
            points.add(PointF(defFull.x1, defFull.y1))
            points.add(PointF(defFull.x2, defFull.y2))
            points.add(PointF(defFull.x3, defFull.y3))
            points.add(PointF(defFull.x4, defFull.y4))
            points.add(PointF(defFull.timeX, defFull.timeY))
            points.add(PointF(defFull.startX, defFull.startY))
            confirmBtn.isEnabled = true
            overlay.invalidate()
            Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show()
        }

        exitBtn.setOnClickListener {
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            FloatWindow.get(this).show(ConfigStore.load(this))
            finish()
        }
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val overlayLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val btnBarType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val btnBarLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, btnBarType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 20.dpToPx(); y = 50.dpToPx() }

        try { wm.addView(overlay, overlayLp) } catch (_: Exception) {}
        try { wm.addView(btnBar, btnBarLp) } catch (_: Exception) {}
    }
    // ==================== 第 2 套校准（4 榜单 8 点） ====================
    private fun showCalibrateAuto() {
        try { FloatWindow.get(this).hide() } catch (_: Exception) {}
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val dp = resources.displayMetrics.density
        fun Int.dpToPx() = (this * dp).toInt()

        val points = mutableListOf<PointF>()
        val oldCfg = ConfigStore.load(this)
        val oldAuto = oldCfg.auto
        if (oldAuto != null) {
            points.add(PointF(oldAuto.rank1X, oldAuto.rank1Y))
            points.add(PointF(oldAuto.rank2X, oldAuto.rank2Y))
            points.add(PointF(oldAuto.rank3X, oldAuto.rank3Y))
            points.add(PointF(oldAuto.rank4X, oldAuto.rank4Y))
            points.add(PointF(oldAuto.confirmX, oldAuto.confirmY))
            points.add(PointF(oldAuto.recruitX, oldAuto.recruitY))
            points.add(PointF(oldAuto.skipX, oldAuto.skipY))
            points.add(PointF(oldAuto.closeX, oldAuto.closeY))
        }

        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x88000000.toInt())
            setStroke(2, 0x88FFFFFF.toInt())
            cornerRadius = 30f
        }

        val confirmBtn = Button(this).apply {
            text = "确定"
            isEnabled = points.size >= 8
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx())
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        val cancelBtn = Button(this).apply {
            text = "重来"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        val resetBtn = Button(this).apply {
            text = "默认"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }
        val exitBtn = Button(this).apply {
            text = "退出"
            layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }

        val btnBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x00000000)
            setPadding(10, 10, 10, 10)
            addView(confirmBtn)
            addView(cancelBtn)
            addView(resetBtn)
            addView(exitBtn)
        }

        var isPass = false
        val toggleBtn = Button(this).apply {
            text = "穿透:关"
            layoutParams = LinearLayout.LayoutParams(100.dpToPx(), 50.dpToPx())
            setTextColor(0xFFFFFFFF.toInt())
            background = btnBg
            stateListAnimator = null
        }

        fun isColorMatch(c: Int, target: Int, tol: Int = 60): Boolean {
            if (target == 0) return true
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val tr = (target shr 16) and 0xFF; val tg = (target shr 8) and 0xFF; val tb = target and 0xFF
            return Math.abs(r - tr) <= tol && Math.abs(g - tg) <= tol && Math.abs(b - tb) <= tol
        }

        val overlay = object : View(this) {
            var isPassThrough = false
            var draggingIdx = -1
            val dotPaint = Paint().apply { color = 0x8800FFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val dotStroke = Paint().apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
            val textPaint = Paint().apply { color = Color.YELLOW; setShadowLayer(8f, 0f, 0f, Color.BLACK); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }

            fun dp2px(v: Float) = v * resources.displayMetrics.density

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val headerH = height * 0.08f
                textPaint.textSize = width * 0.05f

                val title = if (isPassThrough) {
                    "已暂停拦截：切好后点【穿透:关】继续"
                } else if (points.size < 8) {
                    "点 ${points.size + 1}/8：${when (points.size) {
                        0 -> "点第一个紧急征召(黑色)"
                        1 -> "点第二个紧急征召旁(黑色)(将自动生成3、4)"
                        2 -> "榜单3(自动生成)"
                        3 -> "榜单4(自动生成)"
                        4 -> "点紧急征召的确定(蓝色)"
                        5 -> "点一键录取(蓝色)"
                        6 -> "点SKIP(白色)"
                        7 -> "点关闭的字(蓝色)"
                        else -> ""
                    }}"
                } else "可拖动微调"

                canvas.drawText(title, width * 0.03f, headerH * 0.65f, textPaint)

                points.forEachIndexed { i, p ->
                    val paint = if (i in 2..3) {
                        Paint().apply { color = 0x88FFA500.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
                    } else {
                        dotPaint
                    }
                    canvas.drawCircle(p.x, p.y, dp2px(14f), paint)
                    canvas.drawCircle(p.x, p.y, dp2px(14f), dotStroke)
                    val numPaint = Paint().apply { color = 0xFFFFFFFF.toInt(); textSize = dp2px(16f); isAntiAlias = true; textAlign = Paint.Align.CENTER }
                    canvas.drawText("${i + 1}", p.x, p.y + dp2px(6f), numPaint)
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (isPassThrough) return false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        draggingIdx = -1
                        var minDist = Float.MAX_VALUE; var hit = -1
                        points.forEachIndexed { i, p ->
                            val d = Math.hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
                            if (d < minDist && d < dp2px(25f)) { minDist = d; hit = i }
                        }
                        if (hit >= 0 && hit != 2 && hit != 3) {
                            draggingIdx = hit
                        } else if (hit == 2 || hit == 3) {
                            Toast.makeText(context, "榜单3、4由1、2点自动计算，不可单独调整", Toast.LENGTH_SHORT).show()
                        } else if (points.size < 8) {
                            val bmp = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                            val clickColor = if (bmp != null) {
                                val px = bmp.getPixel(e.x.toInt().coerceIn(0, bmp.width - 1), e.y.toInt().coerceIn(0, bmp.height - 1))
                                bmp.recycle()
                                px
                            } else 0

                            val checkTarget = when (points.size) {
                                in 0..3 -> 0xFF4F4747.toInt()
                                4 -> 0xFFA1C2FF.toInt()
                                5 -> 0xFFAFD0FF.toInt()
                                6 -> 0xFFFFFFFF.toInt()
                                7 -> 0xFFACCDFE.toInt()
                                else -> 0
                            }

                            if (checkTarget != 0 && !isColorMatch(clickColor, checkTarget)) {
                                Toast.makeText(context, "⚠️ 颜色不对，请重试", Toast.LENGTH_SHORT).show()
                                return true
                            }

                            points.add(PointF(e.x, e.y))

                            if (points.size == 2) {
                                val p1 = points[0]; val p2 = points[1]
                                val dy = p2.y - p1.y
                                val p3y = p2.y + dy
                                val p4y = p2.y + dy * 2

                                if (p4y > screenH) {
                                    Toast.makeText(context, "间距过大，超出屏幕，请重试", Toast.LENGTH_LONG).show()
                                    points.removeAt(1)
                                } else {
                                    points.add(PointF(p1.x, p3y))
                                    points.add(PointF(p1.x, p4y))
                                    Toast.makeText(context, "✅ 已自动生成榜单3、4", Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (points.size == 8) confirmBtn.isEnabled = true
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggingIdx >= 0) {
                            points[draggingIdx].x = e.x
                            points[draggingIdx].y = e.y
                            if ((draggingIdx == 0 || draggingIdx == 1) && points.size >= 4) {
                                val p1 = points[0]; val p2 = points[1]
                                val dy = p2.y - p1.y
                                points[2].x = p1.x; points[2].y = p2.y + dy
                                points[3].x = p1.x; points[3].y = p2.y + dy * 2
                            }
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        draggingIdx = -1
                        return true
                    }
                }
                return true
            }
        }

        toggleBtn.setOnClickListener {
            val lp = overlay.tag as WindowManager.LayoutParams
            isPass = !isPass
            if (isPass) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                toggleBtn.text = "穿透:开"
            } else {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                toggleBtn.text = "穿透:关"
            }
            wm.updateViewLayout(overlay, lp)
            overlay.invalidate()
        }

        confirmBtn.setOnClickListener {
            if (points.size < 8) { Toast.makeText(this, "请先点满 8 个点", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            ConfigStore.saveAuto(this, points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y, points[3].x, points[3].y, points[4].x, points[4].y, points[5].x, points[5].y, points[6].x, points[6].y, points[7].x, points[7].y)
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { wm.removeView(toggleBtn) } catch (_: Exception) {}
            FloatWindow.get(this).rebuild(ConfigStore.load(this))
            finish()
        }

        cancelBtn.setOnClickListener { points.clear(); confirmBtn.isEnabled = false; overlay.invalidate() }

        resetBtn.setOnClickListener {
            val defAuto = ConfigStore.getDefaultAuto(screenW.toFloat(), screenH.toFloat())
            points.clear()
            points.add(PointF(defAuto.rank1X, defAuto.rank1Y))
            points.add(PointF(defAuto.rank2X, defAuto.rank2Y))
            points.add(PointF(defAuto.rank3X, defAuto.rank3Y))
            points.add(PointF(defAuto.rank4X, defAuto.rank4Y))
            points.add(PointF(defAuto.confirmX, defAuto.confirmY))
            points.add(PointF(defAuto.recruitX, defAuto.recruitY))
            points.add(PointF(defAuto.skipX, defAuto.skipY))
            points.add(PointF(defAuto.closeX, defAuto.closeY))
            confirmBtn.isEnabled = true
            overlay.invalidate()
            Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show()
        }

        exitBtn.setOnClickListener {
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { wm.removeView(toggleBtn) } catch (_: Exception) {} // 穿透按钮也要移除
            FloatWindow.get(this).show(ConfigStore.load(this))
            finish()
        }
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val overlayLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val btnBarLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 15.dpToPx(); y = 50.dpToPx() }

        val toggleBtnLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 20.dpToPx()
            y = 283.dpToPx()
        }

        overlay.tag = overlayLp
        try { wm.addView(overlay, overlayLp) } catch (_: Exception) {}
        try { wm.addView(btnBar, btnBarLp) } catch (_: Exception) {}
        try { wm.addView(toggleBtn, toggleBtnLp) } catch (_: Exception) {}
    }
    // ==================== 辅助方法 ====================
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == RESULT_OK && res.data?.data != null) {
                try {
                    val uri = res.data!!.data!!
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (text != null) {
                        if (text.contains("\"name\"")) {
                            java.io.File(filesDir, "hero.json").writeText(text)
                            Toast.makeText(this, "hero.json 导入成功", Toast.LENGTH_SHORT).show()
                        } else {
                            java.io.File(filesDir, "prophecy.json").writeText(text)
                            Toast.makeText(this, "prophecy.json 导入成功", Toast.LENGTH_SHORT).show()
                        }
                        HeroData.load(this)
                        PropheticWords.load(this)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun importJson() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }

    private fun exportJson() {
        try {
            val heroFile = java.io.File(filesDir, "hero.json")
            val prophecyFile = java.io.File(filesDir, "prophecy.json")
            val exportDir = java.io.File(getExternalFilesDir(null), "export")
            exportDir.mkdirs()
            heroFile.copyTo(java.io.File(exportDir, "hero.json"), overwrite = true)
            prophecyFile.copyTo(java.io.File(exportDir, "prophecy.json"), overwrite = true)
            Toast.makeText(this,
                "已导出到：${exportDir.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showHistory() {
        setContent {
            MaterialTheme {
                Surface {
                    com.rshock.quanjing.ui.HistoryScreen(onBack = { showSettings() })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}