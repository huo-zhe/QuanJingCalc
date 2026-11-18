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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ showCalibrateFull() }, 300)
                moveTaskToBack(true)
            }
            intent?.getBooleanExtra("recalibrate_auto", false) == true -> {
                showHome()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ showCalibrateAuto() }, 300)
                moveTaskToBack(true)
            }
            else -> showHome()
        }
    }

    private fun showHome() {
        setContent {
            MaterialTheme { Surface { HomeScreen(onStartBall = { startBallFlow() }, onOpenSettings = { showSettings() }) } }
        }
    }

    private fun showSettings() {
        setContent {
            MaterialTheme {
                Surface {
                    SettingsScreen(
                        onReCalibrateFull = { showHome(); showCalibrateFull() },
                        onReCalibrateAuto = { showHome(); showCalibrateAuto() },
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            return
        }
        if (ClickAccessibilityService.instance == null) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            return
        }
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (_: Exception) {}
    }

    private fun afterProjectionReady() {
        val cfg = ConfigStore.load(this)
        if (cfg.isFullComplete) { FloatWindow.get(this).show(cfg); finish() } else { showCalibrateFull() }
    }

    // ==================== 独立可拖动方向键面板 ====================
    private fun createDpadPanel(
        ctx: Context, wm: WindowManager,
        onStep: (dx: Float, dy: Float) -> Unit,
        onRelease: () -> Unit
    ): View {
        val dp = ctx.resources.displayMetrics.density
        val size = (40 * dp).toInt()

        val root = FrameLayout(ctx)
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x00000000)
            setStroke((1.5f * dp).toInt(), 0x88FFFFFF.toInt())
            cornerRadius = (50 * dp)
        }
        root.background = bg

        fun makeBtn(text: String, dx: Float, dy: Float): TextView {
            return TextView(ctx).apply {
                this.text = text; gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt()); textSize = 18f
                val lp = FrameLayout.LayoutParams(size, size).apply {
                    when {
                        dx < 0 -> { gravity = Gravity.START or Gravity.CENTER_VERTICAL; leftMargin = (10 * dp).toInt() }
                        dx > 0 -> { gravity = Gravity.END or Gravity.CENTER_VERTICAL; rightMargin = (10 * dp).toInt() }
                        dy < 0 -> { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; topMargin = (10 * dp).toInt() }
                        dy > 0 -> { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; bottomMargin = (10 * dp).toInt() }
                    }
                }
                layoutParams = lp

                val runnable = object : Runnable {
                    override fun run() {
                        onStep(dx, dy)
                        postDelayed(this, 50)
                    }
                }
                setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { onStep(dx, dy); postDelayed(runnable, 300); true }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { removeCallbacks(runnable); onRelease(); true }
                        else -> false
                    }
                }
            }
        }

        root.addView(makeBtn("←", -1f, 0f))
        root.addView(makeBtn("↑", 0f, -1f))
        root.addView(makeBtn("↓", 0f, 1f))
        root.addView(makeBtn("→", 1f, 0f))

        // 中间可拖拽的球
        val center = TextView(ctx).apply {
            text = "移"; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xAAFF4081.toInt()); shape = android.graphics.drawable.GradientDrawable.OVAL }
            layoutParams = FrameLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply { gravity = Gravity.CENTER }
        }
        root.addView(center)

        val panelLp = WindowManager.LayoutParams(
            (150 * dp).toInt(), (150 * dp).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER; x = 0; y = 300
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }

        center.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix = panelLp.x; iy = panelLp.y; tx = e.rawX; ty = e.rawY; true }
                    MotionEvent.ACTION_MOVE -> { panelLp.x = ix + (e.rawX - tx).toInt(); panelLp.y = iy + (e.rawY - ty).toInt(); try { wm.updateViewLayout(root, panelLp) } catch (_: Exception) {}; true }
                    else -> true
                }
                return true
            }
        })

        try { wm.addView(root, panelLp) } catch (_: Exception) {}
        return root
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
            points.add(PointF(oldFull.x1, oldFull.y1)); points.add(PointF(oldFull.x2, oldFull.y2))
            points.add(PointF(oldFull.x3, oldFull.y3)); points.add(PointF(oldFull.x4, oldFull.y4))
            points.add(PointF(oldFull.timeX, oldFull.timeY)); points.add(PointF(oldFull.startX, oldFull.startY))
        }
        var selectedIdx = -1
        var dpadView: View? = null

        // ---------- 检测状态（微调期间绝不现场截图） ----------
        var baseBmp: android.graphics.Bitmap? = null                 // 干净基准图：先隐藏自家浮层再截，避免取到自己的圆点颜色
        val lastCheckedKey = HashMap<Int, Long>()                     // 防抖：某点检测后只要不再移动，就不重复弹窗
        var idleRun: Runnable? = null                                 // "一段时间不动"定时器
        var captureClean: ((((android.graphics.Bitmap?) -> Unit)) -> Unit)? = null  // 视图建好后赋值

        fun pointNameOf(idx: Int): String = when (idx) {
            4 -> "第5个点(时间)"
            5 -> "第6个点(开始招募)"
            else -> "第${idx + 1}个点"
        }
        fun targetOf(idx: Int): Int = when (idx) {
            4 -> 0xFF2EBAF8.toInt()
            5 -> 0xFFA1C2FF.toInt()
            else -> 0
        }
        fun posKey(x: Float, y: Float): Long = (x.toInt().toLong() shl 20) or (y.toInt().toLong() and 0xFFFFFL)

        fun cancelIdle() { idleRun?.let { uiHandler.removeCallbacks(it) }; idleRun = null }

        fun mismatchAt(bmp: android.graphics.Bitmap, idx: Int, x: Float, y: Float): Boolean {
            val target = targetOf(idx)
            if (target == 0) return false
            val px = bmp.getPixel(x.toInt().coerceIn(0, bmp.width - 1), y.toInt().coerceIn(0, bmp.height - 1))
            val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
            val tr = (target shr 16) and 0xFF; val tg = (target shr 8) and 0xFF; val tb = target and 0xFF
            return Math.abs(r - tr) > 60 || Math.abs(g - tg) > 60 || Math.abs(b - tb) > 60
        }

        fun doCheck(idx: Int, x: Float, y: Float) {
            if (targetOf(idx) == 0) return
            val bmp = baseBmp
            if (bmp == null) {
                // 还没有干净基准图：先藏起浮层截一张，截完立刻恢复，再对本次结果复检
                val cap = captureClean ?: return
                cap { b ->
                    baseBmp?.recycle(); baseBmp = b
                    if (b != null) {
                        if (mismatchAt(b, idx, x, y)) Toast.makeText(this@MainActivity, "[!] " + pointNameOf(idx) + "颜色不对，请再对准一点", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@MainActivity, "[OK] " + pointNameOf(idx) + "颜色正确", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
            if (mismatchAt(bmp, idx, x, y)) Toast.makeText(this@MainActivity, "[!] " + pointNameOf(idx) + "颜色不对，请再对准一点", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this@MainActivity, "[OK] " + pointNameOf(idx) + "颜色正确", Toast.LENGTH_SHORT).show()
        }

        fun checkWhenMoved(idx: Int) {
            if (idx !in 0..5 || idx >= points.size) return
            val x = points[idx].x; val y = points[idx].y
            val k = posKey(x, y)
            if (lastCheckedKey[idx] == k) return
            lastCheckedKey[idx] = k
            doCheck(idx, x, y)
        }

        fun scheduleIdleCheck() {
            cancelIdle()
            val idx = selectedIdx
            if (idx !in 0..5 || idx >= points.size) return
            val r = Runnable { idleRun = null; checkWhenMoved(idx) }
            idleRun = r
            uiHandler.postDelayed(r, 300L)
        }

        fun clearBase() { baseBmp?.recycle(); baseBmp = null }

        val btnBg = android.graphics.drawable.GradientDrawable().apply { setColor(0x88000000.toInt()); setStroke(2, 0x88FFFFFF.toInt()); cornerRadius = 30f }
        val confirmBtn = Button(this).apply { text = "确定"; isEnabled = points.size == 6; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()); setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val cancelBtn = Button(this).apply { text = "重来"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val resetBtn = Button(this).apply { text = "默认"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val exitBtn = Button(this).apply { text = "退出"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }

        val btnBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0x00000000); setPadding(10, 10, 10, 10); addView(confirmBtn); addView(cancelBtn); addView(resetBtn); addView(exitBtn) }

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

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val title = when {
                    points.size < 4 -> "点 ${points.size + 1}/6：${when (points.size) {
                        0 -> "整个招募条件左上角"; 1 -> "整个招募条件大框右下角"; 2 -> "一二行和一二列的交点(田字中间)"; 3 -> "二三列之间"; else -> ""
                    }}"
                    points.size == 4 -> "点第 5 个：时间按钮(蓝色)"
                    points.size == 5 -> "点第 6 个：开始招募按钮(蓝色)"
                    else -> "微调后自动检测"
                }
                textPaint.textSize = width * 0.05f
                canvas.drawText(title, width * 0.03f, height * 0.08f * 0.65f, textPaint)

                if (points.size >= 4) {
                    val x1 = points[0].x; val y1 = points[0].y; val x2 = points[1].x; val y2 = points[1].y; val x3 = points[2].x; val y3 = points[2].y; val x4 = points[3].x
                    val colW1 = x3 - x1; val colW2 = x4 - x3
                    val rects = listOf(
                        Rect(x1.toInt(), y1.toInt(), (x1 + colW1).toInt(), y3.toInt()), Rect((x1 + colW1).toInt(), y1.toInt(), (x1 + colW1 + colW2).toInt(), y3.toInt()),
                        Rect((x1 + colW1 + colW2).toInt(), y1.toInt(), x2.toInt(), y3.toInt()), Rect(x1.toInt(), y3.toInt(), (x1 + colW1).toInt(), y2.toInt()),
                        Rect((x1 + colW1).toInt(), y3.toInt(), (x1 + colW1 + colW2).toInt(), y2.toInt())
                    )
                    rects.forEachIndexed { i, r ->
                        canvas.drawRect(r, fill); canvas.drawRect(r, stroke)
                        canvas.drawText("${i + 1}", r.left + 10f, r.top + 60f, Paint().apply { color = Color.WHITE; textSize = dp2px(40f); setShadowLayer(6f, 0f, 0f, Color.BLACK); isAntiAlias = true })
                    }
                }

                points.forEachIndexed { i, p ->
                    if (i == selectedIdx) canvas.drawCircle(p.x, p.y, dp2px(18f), Paint().apply { color = 0xFFFFFF00.toInt(); style = Paint.Style.STROKE; strokeWidth = dp2px(4f); isAntiAlias = true })
                    canvas.drawCircle(p.x, p.y, dp2px(12f), when (i) { 4 -> timePaint; 5 -> startPaint; else -> dotPaint })
                    canvas.drawCircle(p.x, p.y, dp2px(12f), dotStroke)
                    val label = when (i) { 4 -> "时"; 5 -> "招"; else -> "${i + 1}" }
                    canvas.drawText(label, p.x - dp2px(8f), p.y + dp2px(8f), Paint().apply { color = Color.BLACK; textSize = dp2px(20f); isAntiAlias = true })
                }
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        var minDist = Float.MAX_VALUE; var hit = -1
                        points.forEachIndexed { i, p ->
                            val d = Math.hypot((e.x - p.x).toDouble(), (e.y - p.y).toDouble()).toFloat()
                            if (d < minDist && d < dp2px(30f)) { minDist = d; hit = i }
                        }
                        if (hit >= 0) {
                            // 触发点 1：切换点 —— 先检测刚才选中的点
                            val prev = selectedIdx
                            if (prev in 0..5 && prev != hit) checkWhenMoved(prev)
                            selectedIdx = hit; draggingIdx = hit; cancelIdle()
                            if (dpadView == null) {
                                dpadView = createDpadPanel(context, wm,
                                    { dx, dy -> if (selectedIdx in 0..5) { cancelIdle(); points[selectedIdx].x += dx; points[selectedIdx].y += dy; invalidate() } },
                                    { scheduleIdleCheck() })
                            }
                        } else if (points.size < 6) {
                            // 点新点：画面可能刚切换，即时截图查一次（仅此一次），并作废旧基准图
                            val bmp = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                            if (bmp != null) {
                                val px = bmp.getPixel(e.x.toInt().coerceIn(0, bmp.width - 1), e.y.toInt().coerceIn(0, bmp.height - 1))
                                val targetColor = when (points.size) { 4 -> 0xFF2EBAF8.toInt(); 5 -> 0xFFA1C2FF.toInt(); else -> 0 }
                                if (targetColor != 0) {
                                    val r = (px shr 16) and 0xFF; val g = (px shr 8) and 0xFF; val b = px and 0xFF
                                    val tr = (targetColor shr 16) and 0xFF; val tg = (targetColor shr 8) and 0xFF; val tb = targetColor and 0xFF
                                    if (Math.abs(r - tr) > 60 || Math.abs(g - tg) > 60 || Math.abs(b - tb) > 60) {
                                        Toast.makeText(context, "颜色不对，请确认点到了按钮上", Toast.LENGTH_SHORT).show(); return true
                                    }
                                }
                            }
                            points.add(PointF(e.x, e.y)); if (points.size == 6) confirmBtn.isEnabled = true
                            clearBase(); lastCheckedKey.clear(); invalidate()
                        } else {
                            // 触发点 2：点空白取消选中（隐藏方向键）—— 检测当前点
                            if (selectedIdx in 0..5) checkWhenMoved(selectedIdx)
                            cancelIdle()
                            selectedIdx = -1
                            try { wm.removeView(dpadView) } catch (_: Exception) {}
                            dpadView = null; invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggingIdx >= 0) { points[draggingIdx].x = e.x; points[draggingIdx].y = e.y; invalidate() }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 触发点 3（拖动形态）：拖动松手即"移动完成"；方向键则走 1.2 秒空闲检测
                        if (draggingIdx >= 0) checkWhenMoved(draggingIdx)
                        draggingIdx = -1; return true
                    }
                }
                return true
            }
        }

        // 干净基准截图：先隐藏所有自家浮层，100ms 后（等合成一帧干净画面）再截，截完恢复
        captureClean = { onDone ->
            val views = listOfNotNull(overlay, btnBar, dpadView)
            views.forEach { it.visibility = View.INVISIBLE }
            CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                val b = withContext(Dispatchers.IO) { com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture() }
                views.forEach { it.visibility = View.VISIBLE }
                onDone(b)
            }
        }

        confirmBtn.setOnClickListener {
            if (points.size < 6) return@setOnClickListener
            val x1 = points[0].x; val y1 = points[0].y; val x2 = points[1].x; val y2 = points[1].y; val x3 = points[2].x; val y3 = points[2].y; val x4 = points[3].x
            val colW1 = x3 - x1; val colW2 = x4 - x3
            val tagCenters = listOf(PointF(x1 + colW1 / 2f, (y1 + y3) / 2f), PointF(x1 + colW1 + colW2 / 2f, (y1 + y3) / 2f), PointF(x1 + colW1 + colW2 + (x2 - (x1 + colW1 + colW2)) / 2f, (y1 + y3) / 2f), PointF(x1 + colW1 / 2f, (y3 + y2) / 2f), PointF(x1 + colW1 + colW2 / 2f, (y3 + y2) / 2f))

            android.util.Log.d("QJ_Default", "=== 6点校准日志 ($screenW x $screenH) ===")
            android.util.Log.d("QJ_Default", "点1(左上): ${points[0].x}f, ${points[0].y}f")
            android.util.Log.d("QJ_Default", "点2(右下): ${points[1].x}f, ${points[1].y}f")
            android.util.Log.d("QJ_Default", "点3(1行1列): ${points[2].x}f, ${points[2].y}f")
            android.util.Log.d("QJ_Default", "点4(1行2列): ${points[3].x}f, ${points[3].y}f")
            android.util.Log.d("QJ_Default", "时间: ${points[4].x}f, ${points[4].y}f")
            android.util.Log.d("QJ_Default", "开始招募: ${points[5].x}f, ${points[5].y}f")
            android.util.Log.d("QJ_Default", "词条1: ${tagCenters[0].x}f, ${tagCenters[0].y}f")
            android.util.Log.d("QJ_Default", "词条2: ${tagCenters[1].x}f, ${tagCenters[1].y}f")
            android.util.Log.d("QJ_Default", "词条3: ${tagCenters[2].x}f, ${tagCenters[2].y}f")
            android.util.Log.d("QJ_Default", "词条4: ${tagCenters[3].x}f, ${tagCenters[3].y}f")
            android.util.Log.d("QJ_Default", "词条5: ${tagCenters[4].x}f, ${tagCenters[4].y}f")

            ConfigStore.saveFull(this, points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y, points[3].x, points[3].y, points[4].x, points[4].y, points[5].x, points[5].y, tagCenters[0].x, tagCenters[0].y, tagCenters[1].x, tagCenters[1].y, tagCenters[2].x, tagCenters[2].y, tagCenters[3].x, tagCenters[3].y, tagCenters[4].x, tagCenters[4].y)
            cancelIdle(); clearBase()
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            FloatWindow.get(this).rebuild(ConfigStore.load(this)); finish()
        }

        cancelBtn.setOnClickListener {
            points.clear(); confirmBtn.isEnabled = false; selectedIdx = -1
            cancelIdle(); clearBase(); lastCheckedKey.clear()
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            dpadView = null; overlay.invalidate()
        }
        resetBtn.setOnClickListener {
            val defFull = ConfigStore.getDefaultFull(screenW.toFloat(), screenH.toFloat())
            points.clear(); points.add(PointF(defFull.x1, defFull.y1)); points.add(PointF(defFull.x2, defFull.y2)); points.add(PointF(defFull.x3, defFull.y3)); points.add(PointF(defFull.x4, defFull.y4)); points.add(PointF(defFull.timeX, defFull.timeY)); points.add(PointF(defFull.startX, defFull.startY))
            confirmBtn.isEnabled = true; clearBase(); lastCheckedKey.clear(); overlay.invalidate(); Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show()
        }
        exitBtn.setOnClickListener {
            cancelIdle(); clearBase()
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            FloatWindow.get(this).show(ConfigStore.load(this)); finish()
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val overlayLp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
        val btnBarLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 20.dpToPx(); y = 50.dpToPx() }
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

        fun isColorMatch(c: Int, target: Int, tol: Int = 60): Boolean {
            if (target == 0) return true
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val tr = (target shr 16) and 0xFF; val tg = (target shr 8) and 0xFF; val tb = target and 0xFF
            return Math.abs(r - tr) <= tol && Math.abs(g - tg) <= tol && Math.abs(b - tb) <= tol
        }

        val points = mutableListOf<PointF>()
        val oldAuto = ConfigStore.load(this).auto
        if (oldAuto != null) {
            points.add(PointF(oldAuto.rank1X, oldAuto.rank1Y)); points.add(PointF(oldAuto.rank2X, oldAuto.rank2Y))
            points.add(PointF(oldAuto.rank3X, oldAuto.rank3Y)); points.add(PointF(oldAuto.rank4X, oldAuto.rank4Y))
            points.add(PointF(oldAuto.confirmX, oldAuto.confirmY)); points.add(PointF(oldAuto.recruitX, oldAuto.recruitY))
            points.add(PointF(oldAuto.skipX, oldAuto.skipY)); points.add(PointF(oldAuto.closeX, oldAuto.closeY))
        }
        var selectedIdx = -1
        var dpadView: View? = null

        // ---------- 检测状态（微调期间绝不现场截图） ----------
        var baseBmp: android.graphics.Bitmap? = null                 // 干净基准图：先隐藏自家浮层再截
        val lastCheckedKey = HashMap<Int, Long>()                     // 防抖：点没再移动就不重复弹窗
        var idleRun: Runnable? = null                                 // "一段时间不动"定时器
        var captureClean: ((((android.graphics.Bitmap?) -> Unit)) -> Unit)? = null  // 视图建好后赋值

        fun pointNameOf(idx: Int): String = when (idx) {
            0 -> "第1个点(紧急征召1)"; 1 -> "第2个点(紧急征召2)"; 2 -> "第3个点(榜单3)"; 3 -> "第4个点(榜单4)"
            4 -> "第5个点(确定)"; 5 -> "第6个点(一键录取)"; 6 -> "第7个点(SKIP)"; 7 -> "第8个点(关闭)"
            else -> "第${idx + 1}个点"
        }
        fun targetOf(idx: Int): Int = when (idx) {
            in 0..3 -> 0xFF4F4747.toInt()
            4 -> 0xFFA1C2FF.toInt()
            5 -> 0xFFAFD0FF.toInt()
            6 -> 0xFFFFFFFF.toInt()
            7 -> 0xFFACCDFE.toInt()
            else -> 0
        }
        fun posKey(x: Float, y: Float): Long = (x.toInt().toLong() shl 20) or (y.toInt().toLong() and 0xFFFFFL)

        fun cancelIdle() { idleRun?.let { uiHandler.removeCallbacks(it) }; idleRun = null }

        fun mismatchAt(bmp: android.graphics.Bitmap, idx: Int, x: Float, y: Float): Boolean {
            val target = targetOf(idx)
            if (target == 0) return false
            val px = bmp.getPixel(x.toInt().coerceIn(0, bmp.width - 1), y.toInt().coerceIn(0, bmp.height - 1))
            return !isColorMatch(px, target)
        }

        fun doCheck(idx: Int, x: Float, y: Float) {
            if (targetOf(idx) == 0) return
            val bmp = baseBmp
            if (bmp == null) {
                val cap = captureClean ?: return
                cap { b ->
                    baseBmp?.recycle(); baseBmp = b
                    if (b != null) {
                        if (mismatchAt(b, idx, x, y)) Toast.makeText(this@MainActivity, "[!] " + pointNameOf(idx) + "颜色不对，请再对准一点", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(this@MainActivity, "[OK] " + pointNameOf(idx) + "颜色正确", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
            if (mismatchAt(bmp, idx, x, y)) Toast.makeText(this@MainActivity, "[!] " + pointNameOf(idx) + "颜色不对，请再对准一点", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this@MainActivity, "[OK] " + pointNameOf(idx) + "颜色正确", Toast.LENGTH_SHORT).show()
        }

        fun checkWhenMoved(idx: Int) {
            if (idx !in 0..7 || idx >= points.size) return
            val x = points[idx].x; val y = points[idx].y
            val k = posKey(x, y)
            if (lastCheckedKey[idx] == k) return
            lastCheckedKey[idx] = k
            doCheck(idx, x, y)
        }

        fun scheduleIdleCheck() {
            cancelIdle()
            val idx = selectedIdx
            if (idx !in 0..7 || idx >= points.size) return
            val r = Runnable { idleRun = null; checkWhenMoved(idx) }
            idleRun = r
            uiHandler.postDelayed(r, 300L)
        }

        fun clearBase() { baseBmp?.recycle(); baseBmp = null }

        val btnBg = android.graphics.drawable.GradientDrawable().apply { setColor(0x88000000.toInt()); setStroke(2, 0x88FFFFFF.toInt()); cornerRadius = 30f }
        val confirmBtn = Button(this).apply { text = "确定"; isEnabled = points.size >= 8; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()); setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val cancelBtn = Button(this).apply { text = "重来"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val resetBtn = Button(this).apply { text = "默认"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val exitBtn = Button(this).apply { text = "退出"; layoutParams = LinearLayout.LayoutParams(90.dpToPx(), 50.dpToPx()).apply { topMargin = 15 }; setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }
        val toggleBtn = Button(this).apply { text = "穿透:关"; layoutParams = LinearLayout.LayoutParams(100.dpToPx(), 50.dpToPx()); setTextColor(0xFFFFFFFF.toInt()); background = btnBg; stateListAnimator = null }

        val btnBar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0x00000000); setPadding(10, 10, 10, 10); addView(confirmBtn); addView(cancelBtn); addView(resetBtn); addView(exitBtn) }
        var isPass = false

        val overlay = object : View(this) {
            var isPassThrough = false
            var draggingIdx = -1
            val dotPaint = Paint().apply { color = 0x8800FFFF.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
            val dotStroke = Paint().apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
            val textPaint = Paint().apply { color = Color.YELLOW; setShadowLayer(8f, 0f, 0f, Color.BLACK); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            fun dp2px(v: Float) = v * resources.displayMetrics.density

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                textPaint.textSize = width * 0.05f
                val title = if (isPassThrough) "已暂停拦截：切好后点【穿透:关】继续"
                else if (points.size < 8) "点 ${points.size + 1}/8：${when (points.size) { 0 -> "第一个紧急征召(黑色)"; 1 -> "第二个紧急征召旁(黑色)(自动生成3、4)"; 2 -> "榜单3(自动生成)"; 3 -> "榜单4(自动生成)"; 4 -> "确定(蓝色)"; 5 -> "一键录取(蓝色)"; 6 -> "SKIP(白色)"; 7 -> "关闭(蓝色)"; else -> "" }}"
                else "微调后自动检测"
                canvas.drawText(title, width * 0.03f, height * 0.08f * 0.65f, textPaint)

                points.forEachIndexed { i, p ->
                    if (i == selectedIdx) canvas.drawCircle(p.x, p.y, dp2px(18f), Paint().apply { color = 0xFFFFFF00.toInt(); style = Paint.Style.STROKE; strokeWidth = dp2px(4f); isAntiAlias = true })
                    val paint = if (i in 2..3) Paint().apply { color = 0x88FFA500.toInt(); style = Paint.Style.FILL; isAntiAlias = true } else dotPaint
                    canvas.drawCircle(p.x, p.y, dp2px(12f), paint); canvas.drawCircle(p.x, p.y, dp2px(12f), dotStroke)
                    canvas.drawText("${i + 1}", p.x - dp2px(8f), p.y + dp2px(6f), Paint().apply { color = Color.WHITE; textSize = dp2px(16f); isAntiAlias = true })
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
                            if (d < minDist && d < dp2px(30f)) { minDist = d; hit = i }
                        }
                        if (hit >= 0) {
                            // 触发点 1：切换点 —— 先检测刚才选中的点
                            val prev = selectedIdx
                            if (prev in 0..7 && prev != hit) checkWhenMoved(prev)
                            selectedIdx = hit; cancelIdle()
                            if (dpadView == null) {
                                dpadView = createDpadPanel(context, wm,
                                    { dx, dy -> if (selectedIdx in 0..7) { cancelIdle(); points[selectedIdx].x += dx; points[selectedIdx].y += dy; if (selectedIdx in 0..1 && points.size >= 4) { val dY = points[1].y - points[0].y; points[2].x = points[0].x; points[2].y = points[1].y + dY; points[3].x = points[0].x; points[3].y = points[1].y + dY * 2 }; invalidate() } },
                                    { scheduleIdleCheck() })
                            }
                            if (hit != 2 && hit != 3) draggingIdx = hit else Toast.makeText(context, "榜单3、4自动计算，可微调1、2", Toast.LENGTH_SHORT).show()
                        } else if (points.size < 8) {
                            // 点新点：画面可能刚切换，即时截图查一次（仅此一次），并作废旧基准图
                            val bmp = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                            val clickColor = if (bmp != null) bmp.getPixel(e.x.toInt().coerceIn(0, bmp.width - 1), e.y.toInt().coerceIn(0, bmp.height - 1)) else 0
                            val checkTarget = when (points.size) { in 0..3 -> 0xFF4F4747.toInt(); 4 -> 0xFFA1C2FF.toInt(); 5 -> 0xFFAFD0FF.toInt(); 6 -> 0xFFFFFFFF.toInt(); 7 -> 0xFFACCDFE.toInt(); else -> 0 }
                            if (checkTarget != 0 && !isColorMatch(clickColor, checkTarget)) { Toast.makeText(context, "颜色不对，请重试", Toast.LENGTH_SHORT).show(); return true }
                            points.add(PointF(e.x, e.y))
                            if (points.size == 2) {
                                val p1 = points[0]; val p2 = points[1]; val dy = p2.y - p1.y; val p3y = p2.y + dy; val p4y = p2.y + dy * 2
                                if (p4y > screenH) { Toast.makeText(context, "间距过大，超出屏幕", Toast.LENGTH_LONG).show(); points.removeAt(1) }
                                else { points.add(PointF(p1.x, p3y)); points.add(PointF(p1.x, p4y)); Toast.makeText(context, "已自动生成榜单3、4", Toast.LENGTH_SHORT).show() }
                            }
                            if (points.size == 8) confirmBtn.isEnabled = true
                            clearBase(); lastCheckedKey.clear(); invalidate()
                        } else {
                            // 触发点 2：点空白取消选中（隐藏方向键）—— 检测当前点
                            if (selectedIdx in 0..7) checkWhenMoved(selectedIdx)
                            cancelIdle()
                            selectedIdx = -1
                            try { wm.removeView(dpadView) } catch (_: Exception) {}
                            dpadView = null; invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggingIdx >= 0) {
                            points[draggingIdx].x = e.x; points[draggingIdx].y = e.y
                            if ((draggingIdx == 0 || draggingIdx == 1) && points.size >= 4) { val p1 = points[0]; val p2 = points[1]; val dy = p2.y - p1.y; points[2].x = p1.x; points[2].y = p2.y + dy; points[3].x = p1.x; points[3].y = p2.y + dy * 2 }
                            invalidate()
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 触发点 3（拖动形态）：拖动松手即"移动完成"；方向键则走 1.2 秒空闲检测
                        if (draggingIdx >= 0) checkWhenMoved(draggingIdx)
                        draggingIdx = -1; return true
                    }
                }
                return true
            }
        }

        // 干净基准截图：先隐藏所有自家浮层，100ms 后再截，截完恢复
        captureClean = { onDone ->
            val views = listOfNotNull(overlay, btnBar, toggleBtn, dpadView)
            views.forEach { it.visibility = View.INVISIBLE }
            CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                val b = withContext(Dispatchers.IO) { com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture() }
                views.forEach { it.visibility = View.VISIBLE }
                onDone(b)
            }
        }

        toggleBtn.setOnClickListener {
            val lp = overlay.tag as WindowManager.LayoutParams
            isPass = !isPass
            if (isPass) { lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; toggleBtn.text = "穿透:开" } else { lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); toggleBtn.text = "穿透:关" }
            wm.updateViewLayout(overlay, lp); clearBase(); overlay.invalidate()
        }

        confirmBtn.setOnClickListener {
            if (points.size < 8) return@setOnClickListener

            android.util.Log.d("QJ_Default", "=== 8点校准日志 ($screenW x $screenH) ===")
            android.util.Log.d("QJ_Default", "榜单1: ${points[0].x}f, ${points[0].y}f")
            android.util.Log.d("QJ_Default", "榜单2: ${points[1].x}f, ${points[1].y}f")
            android.util.Log.d("QJ_Default", "榜单3: ${points[2].x}f, ${points[2].y}f")
            android.util.Log.d("QJ_Default", "榜单4: ${points[3].x}f, ${points[3].y}f")
            android.util.Log.d("QJ_Default", "确定: ${points[4].x}f, ${points[4].y}f")
            android.util.Log.d("QJ_Default", "录取: ${points[5].x}f, ${points[5].y}f")
            android.util.Log.d("QJ_Default", "SKIP: ${points[6].x}f, ${points[6].y}f")
            android.util.Log.d("QJ_Default", "关闭: ${points[7].x}f, ${points[7].y}f")

            ConfigStore.saveAuto(this, points[0].x, points[0].y, points[1].x, points[1].y, points[2].x, points[2].y, points[3].x, points[3].y, points[4].x, points[4].y, points[5].x, points[5].y, points[6].x, points[6].y, points[7].x, points[7].y)
            cancelIdle(); clearBase()
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { wm.removeView(toggleBtn) } catch (_: Exception) {}
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            FloatWindow.get(this).rebuild(ConfigStore.load(this)); finish()
        }

        cancelBtn.setOnClickListener {
            points.clear(); confirmBtn.isEnabled = false; selectedIdx = -1
            cancelIdle(); clearBase(); lastCheckedKey.clear()
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            dpadView = null; overlay.invalidate()
        }
        resetBtn.setOnClickListener {
            val defAuto = ConfigStore.getDefaultAuto(screenW.toFloat(), screenH.toFloat())
            points.clear(); points.add(PointF(defAuto.rank1X, defAuto.rank1Y)); points.add(PointF(defAuto.rank2X, defAuto.rank2Y)); points.add(PointF(defAuto.rank3X, defAuto.rank3Y)); points.add(PointF(defAuto.rank4X, defAuto.rank4Y)); points.add(PointF(defAuto.confirmX, defAuto.confirmY)); points.add(PointF(defAuto.recruitX, defAuto.recruitY)); points.add(PointF(defAuto.skipX, defAuto.skipY)); points.add(PointF(defAuto.closeX, defAuto.closeY))
            confirmBtn.isEnabled = true; clearBase(); lastCheckedKey.clear(); overlay.invalidate(); Toast.makeText(this, "已恢复默认坐标", Toast.LENGTH_SHORT).show()
        }
        exitBtn.setOnClickListener {
            cancelIdle(); clearBase()
            try { wm.removeView(overlay) } catch (_: Exception) {}
            try { wm.removeView(btnBar) } catch (_: Exception) {}
            try { wm.removeView(toggleBtn) } catch (_: Exception) {}
            try { if (dpadView != null) wm.removeView(dpadView) } catch (_: Exception) {}
            FloatWindow.get(this).show(ConfigStore.load(this)); finish()
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val overlayLp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
        val btnBarLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 15.dpToPx(); y = 50.dpToPx() }
        val toggleBtnLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.START; x = 20.dpToPx(); y = 283.dpToPx() }

        overlay.tag = overlayLp
        try { wm.addView(overlay, overlayLp) } catch (_: Exception) {}
        try { wm.addView(btnBar, btnBarLp) } catch (_: Exception) {}
        try { wm.addView(toggleBtn, toggleBtnLp) } catch (_: Exception) {}
    }
    // ==================== 辅助方法 ====================
    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK && res.data?.data != null) {
            try {
                val uri = res.data!!.data!!
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    if (text.contains("\"name\"")) { java.io.File(filesDir, "hero.json").writeText(text); Toast.makeText(this, "hero.json 导入成功", Toast.LENGTH_SHORT).show() }
                    else { java.io.File(filesDir, "prophecy.json").writeText(text); Toast.makeText(this, "prophecy.json 导入成功", Toast.LENGTH_SHORT).show() }
                    HeroData.load(this); PropheticWords.load(this)
                }
            } catch (e: Exception) { Toast.makeText(this, "导入失败：${e.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun importJson() { importLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }) }
    private fun exportJson() {
        try {
            val heroFile = java.io.File(filesDir, "hero.json"); val prophecyFile = java.io.File(filesDir, "prophecy.json")
            val exportDir = java.io.File(getExternalFilesDir(null), "export"); exportDir.mkdirs()
            heroFile.copyTo(java.io.File(exportDir, "hero.json"), overwrite = true); prophecyFile.copyTo(java.io.File(exportDir, "prophecy.json"), overwrite = true)
            Toast.makeText(this, "已导出到：${exportDir.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show() }
    }
    private fun showHistory() { setContent { MaterialTheme { Surface { com.rshock.quanjing.ui.HistoryScreen(onBack = { showSettings() }) } } } }
    override fun onDestroy() { super.onDestroy() }
}