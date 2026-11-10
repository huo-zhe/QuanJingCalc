package com.rshock.quanjing.ui

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rshock.quanjing.auto.AutoRunner
import com.rshock.quanjing.core.Advice
import com.rshock.quanjing.core.Suggestion
import com.rshock.quanjing.util.ConfigStore
import com.rshock.quanjing.util.HistoryStore
import com.rshock.quanjing.util.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.rshock.quanjing.auto.ClickAccessibilityService

class FloatWindow private constructor(private val ctx: Context) {

    companion object {
        private const val PREF = "qj_ball"
        private const val KEY_SHOWING = "showing"

        @Volatile private var instance: FloatWindow? = null

        fun get(ctx: Context): FloatWindow =
            instance ?: synchronized(this) {
                instance ?: FloatWindow(ctx.applicationContext).also { instance = it }
            }

        fun reset() {
            synchronized(this) { instance = null }
        }
    }

    @Volatile private var isFullAutoRunning = false
    private var fullAutoJob: kotlinx.coroutines.Job? = null

    private fun startFullAuto() {
        val r = rois
        if (r?.full == null || r.auto == null) {
            showToast("请先完成两套校准")
            return
        }
        isFullAutoRunning = true
        // 悬浮球变红 + 显示"停"
        (ball as? TextView)?.apply {
            text = "停"
            val sizeDp = SettingsStore.ballSizeDp
            val alpha = SettingsStore.ballAlpha
            val shape = SettingsStore.ballShape
            val sizePx = (sizeDp * ctx.resources.displayMetrics.density).toInt()
            val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
            // 红色的半透明底色
            val redColor = (alphaInt shl 24) or 0xFF4444

            background = android.graphics.drawable.GradientDrawable().apply {
                if (shape == "circle") {
                    this.shape = android.graphics.drawable.GradientDrawable.OVAL
                } else {
                    this.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = sizePx * 0.15f
                }
                setColor(redColor)
            }
        }
        fullAutoJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                AutoRunner.runFullAuto(
                    roiSet = r,
                    onProgress = { msg ->
                        mainHandler.post { showToast(msg) }
                    },
                    shouldStop = { !isFullAutoRunning },
                    onLeaderFound = { report ->
                        mainHandler.post {
                            // 弹领袖面板
                            showResultPanel(report)
                        }
                    }
                )
            } catch (e: Exception) {
                mainHandler.post { showToast("全自动已停止") }
            } finally {
                mainHandler.post { resetBall() }
            }
        }
    }

    private fun stopFullAuto() {
        isFullAutoRunning = false
        fullAutoJob?.cancel()
        fullAutoJob = null
        resetBall()
    }

    private fun resetBall() {
        isFullAutoRunning = false
        (ball as? TextView)?.apply {
            text = "全"
            setBackgroundColor(0xCC4A90D9.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                if (SettingsStore.ballShape == "circle") {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                } else {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 30f
                }
                setColor(0xCC4A90D9.toInt())
            }
        }
    }

    private var ball: View? = null
    private var trashView: View? = null
    private var roiOverlay: View? = null
    private lateinit var wm: WindowManager
    private var isRebuilding = false

    private var rois: ConfigStore.RoiSet? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prefs() = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun setShowing(v: Boolean) = prefs().edit().putBoolean(KEY_SHOWING, v).apply()

    fun show(rois: ConfigStore.RoiSet) {
        if (ball != null) return
        if (!isRebuilding && prefs().getBoolean(KEY_SHOWING, false)) return

        this.rois = rois
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val sizeDp = SettingsStore.ballSizeDp
        val alpha = SettingsStore.ballAlpha
        val shape = SettingsStore.ballShape
        val sizePx = (sizeDp * ctx.resources.displayMetrics.density).toInt()
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
        val bgColor = (alphaInt shl 24) or 0x4A90D9

        val ballView = TextView(ctx).apply {
            text = "全"
            textSize = (sizeDp * 0.36f)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                if (shape == "circle") {
                    this.shape = android.graphics.drawable.GradientDrawable.OVAL
                } else {
                    this.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = sizePx * 0.15f
                }
                setColor(bgColor)
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN, // ★ 全屏
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            // ★ 加上初始位置，比如距离左边 50，距离顶部 300
            x = 50
            y = 500

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }

            // ★ 关键：无视所有系统安全区
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }

        ballView.setOnTouchListener(object : View.OnTouchListener {
            private var ix = 0; private var iy = 0
            private var tx = 0f; private var ty = 0f
            private var downT = 0L
            private var moved = false
            private var longTriggered = false

            private val longRunnable = Runnable {
                if (!moved && !longTriggered) {
                    longTriggered = true
                    showLongPressMenu()
                }
            }

            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = lp.x; iy = lp.y
                        tx = e.rawX; ty = e.rawY
                        downT = System.currentTimeMillis()
                        moved = false
                        longTriggered = false
                        ballView.postDelayed(longRunnable, 500)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx; val dy = e.rawY - ty
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            moved = true
                            if (trashView == null) {
                                val tv = TextView(ctx).apply {
                                    text = "🗑 松手关闭"
                                    textSize = 16f
                                    setTextColor(0xFFFFFFFF.toInt())
                                    gravity = Gravity.CENTER
                                    setBackgroundColor(0xCCFF4444.toInt())
                                    setPadding(30, 20, 30, 20)
                                }
                                val tlp = WindowManager.LayoutParams(
                                    WindowManager.LayoutParams.WRAP_CONTENT,
                                    WindowManager.LayoutParams.WRAP_CONTENT,
                                    type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                                    PixelFormat.TRANSLUCENT
                                ).apply {
                                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                                    y = 150
                                }
                                try { wm.addView(tv, tlp); trashView = tv } catch (_: Exception) {}
                            }
                        }

                        // ★ 强制获取绝对物理屏幕尺寸
                        val screenW = ctx.resources.displayMetrics.widthPixels
                        val wmMetrics = android.util.DisplayMetrics()
                        @Suppress("DEPRECATION")
                        wm.defaultDisplay.getRealMetrics(wmMetrics)
                        val screenH = wmMetrics.heightPixels

                        // ★ 用物理高度限制边界，让它能拖到最底下
                        lp.x = (ix + dx.toInt()).coerceIn(0, screenW - sizePx)
                        lp.y = (iy + dy.toInt()).coerceIn(0, screenH - sizePx)

                        wm.updateViewLayout(ballView, lp)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        ballView.removeCallbacks(longRunnable)
                        trashView?.let { try { wm.removeView(it) } catch (_: Exception) {}; trashView = null }

                        // ★ 关闭按钮的判定区域也需要用真实物理高度
                        val screenW = ctx.resources.displayMetrics.widthPixels
                        val wmMetrics = android.util.DisplayMetrics()
                        @Suppress("DEPRECATION")
                        wm.defaultDisplay.getRealMetrics(wmMetrics)
                        val screenH = wmMetrics.heightPixels

                        val ballCenterX = lp.x + sizePx / 2f
                        val ballCenterY = lp.y + sizePx / 2f
                        val closeZoneTop = screenH - 250f // 底部 250 像素内算作关闭区

                        if (ballCenterY > closeZoneTop &&
                            Math.abs(ballCenterX - screenW / 2f) < screenW * 0.2f) {
                            try { wm.removeView(ballView) } catch (_: Exception) {}
                            ball = null
                            setShowing(false)
                            return true
                        }

                        if (!moved && !longTriggered && System.currentTimeMillis() - downT < 500) {
                            runOneClick()
                        }
                    }
                }
                return true
            }
        })

        try {
            wm.addView(ballView, lp)
            ball = ballView
            setShowing(true)
        } catch (_: Exception) {}
    }

    fun rebuild(newRois: ConfigStore.RoiSet? = null) {
        if (newRois != null) rois = newRois
        val r = rois ?: return
        isRebuilding = true
        try { ball?.let { wm.removeView(it) } } catch (_: Exception) {}
        ball = null
        show(r)
        isRebuilding = false
    }

    private fun runOneClick() {
        if (isFullAutoRunning) {
            stopFullAuto()
            return
        }
        val r = rois ?: return
        ball?.visibility = View.GONE

        // ★ 判断当前是不是面板模式
        val isPanelMode = SettingsStore.mode == "panel"

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val full = r.full
                if (full != null) {
                    val (tx, ty) = full.timePoint() // 去掉参数
                    ClickAccessibilityService.instance?.tap(tx, ty)
                }
                val report = AutoRunner.analyze(r)
                mainHandler.post {
                    ball?.visibility = View.VISIBLE

                    when (report) {
                        is AutoRunner.Report2.Failed -> {
                            if (SettingsStore.debugMode) showFailDebugPanel(report)
                            else showToast("识别失败：${report.reason}")
                        }
                        is AutoRunner.Report2.Success -> {
                            // ★ 核心分流
                            if (isPanelMode) {
                                showResultPanel(report) // 走面板模式：弹列表
                            } else {
                                // 走悬浮球模式：直接操作
                                when (val a = report.advice) {
                                    is Advice.NoMatch -> showToast("无匹配角色")
                                    is Advice.NoLeader -> {
                                        val best = a.allCombos.firstOrNull()
                                        if (best != null) {
                                            // ★ 补上点击词条的逻辑
                                            CoroutineScope(Dispatchers.Default).launch {
                                                try {
                                                    AutoRunner.autoPick(best, report.tagRects)
                                                } catch (_: Exception) {}
                                                mainHandler.post {
                                                    showToast("已选中：${best.tags.joinToString("/")}\n请手动点「开始招募」")
                                                }
                                            }
                                        }
                                    }                                    is Advice.HasLeader -> showResultPanel(report)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    ball?.visibility = View.VISIBLE
                    showToast("异常：${e.message}")
                }
            }
        }
    }

    private fun handleSuccess(report: AutoRunner.Report2.Success) {
        when (val a = report.advice) {
            is Advice.NoMatch -> showToast("无匹配角色")
            is Advice.NoLeader -> {
                val best = a.allCombos.firstOrNull() ?: return
                HistoryStore.add(
                    ctx,
                    report.tags.joinToString("\t"),
                    best.tags.joinToString("\t") + " → " + best.topLevel + "★"
                )
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        AutoRunner.autoPick(best, report.tagRects)
                    } catch (_: Exception) {}
                    mainHandler.post {
                        showToast("已选中：${best.tags.joinToString("/")}")
                    }
                }
            }
            is Advice.HasLeader -> showResultPanel(report)
        }
    }

    private fun showResultPanel(report: AutoRunner.Report2.Success) {
        val allSuggestions = when (val a = report.advice) {
            is Advice.HasLeader -> a.all3StarCombos
            is Advice.NoLeader -> a.allCombos
            else -> emptyList()
        }
        showResultPanelWithSuggestions(report, allSuggestions)
    }

    private fun showResultPanelWithSuggestions(
        report: AutoRunner.Report2.Success,
        allSuggestions: List<Suggestion>
    ) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC1E1E2E.toInt())
            setPadding(30, 30, 30, 30)
        }

        val isLeaderCase = report.advice is Advice.HasLeader
        val headerText = if (isLeaderCase) "⚠️ 检测到领袖（可出 3★）" else "结果"
        root.addView(TextView(ctx).apply {
            text = headerText
            setTextColor(if (isLeaderCase) 0xFFFFD700.toInt() else 0xFFFFFFFF.toInt())
            textSize = 15f
        })

        if (SettingsStore.debugMode) {
            val sb = StringBuilder()
            sb.append("OCR 每位置：\n")
            for (i in 1..5) {
                sb.append("  位置 $i: ${report.ocrAtPos.getOrNull(i) ?: "(未读到)"}\n")
            }
            sb.append("\n结果：${report.tags.joinToString("\t")}")
            root.addView(TextView(ctx).apply {
                text = sb.toString()
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                setPadding(0, 8, 0, 8)
            })
        }

        val scroll = ScrollView(ctx)
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        allSuggestions.forEach { combo ->
            val levels = combo.heroes.map { it.level }.toSet()
            val (colors, textColor) = when {
                levels == setOf(3) -> intArrayOf(
                    0xFFFF00FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD700.toInt()
                ) to 0xFF000000.toInt()
                levels == setOf(2) -> intArrayOf(
                    0xFFFFFFFF.toInt(), 0xFFFFD700.toInt(), 0xFFFFFFFF.toInt()
                ) to 0xFF000000.toInt()
                else -> intArrayOf(
                    0xFF2A2A3A.toInt(), 0xFF1A1A2A.toInt()
                ) to 0xFFFFFFFF.toInt()
            }

            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(30, 20, 30, 20)
                val clp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                clp.bottomMargin = 15
                layoutParams = clp
                background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.BL_TR,
                    colors
                ).apply { cornerRadius = 20f }
            }

            card.addView(TextView(ctx).apply {
                text = combo.tags.joinToString(" / ")
                setTextColor(textColor); textSize = 16f
            })
            card.addView(TextView(ctx).apply {
                text = combo.heroes.joinToString("、") { it.shortName }
                setTextColor(textColor); textSize = 13f
                setPadding(0, 8, 0, 0)
            })
            card.setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                if (combo.hasLeader) {
                    showConfirmOverlay(combo, report, allSuggestions)
                } else {
                    HistoryStore.add(
                        ctx,
                        report.tags.joinToString("\t"),
                        combo.tags.joinToString("\t") + " → " + combo.topLevel + "★"
                    )
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            AutoRunner.autoPick(combo, report.tagRects)
                        } catch (_: Exception) {}
                        mainHandler.post {
                            showToast("已选中：${combo.tags.joinToString("/")}")
                        }
                    }
                }
            }
            inner.addView(card)
        }

        scroll.addView(inner)

        val screenH = ctx.resources.displayMetrics.heightPixels
        val maxPanelH = (screenH * 0.35f).toInt()
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            maxPanelH
        ))

        root.addView(Button(ctx).apply {
            text = "取消"
            setOnClickListener { try { wm.removeView(root) } catch (_: Exception) {} }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )        .apply { gravity = Gravity.BOTTOM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // ★ 关键：无视所有系统安全区
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }

    private fun showConfirmOverlay(
        combo: Suggestion,
        report: AutoRunner.Report2.Success,
        allSuggestions: List<Suggestion>
    ) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1E1E2E.toInt())
            setPadding(40, 40, 40, 40)
        }

        root.addView(TextView(ctx).apply {
            text = "⚠️ 确认点击以下词条？"
            setTextColor(0xFFFFD700.toInt()); textSize = 18f
            setPadding(0, 0, 0, 20)
        })
        root.addView(TextView(ctx).apply {
            text = "组合：${combo.tags.joinToString(" / ")}"
            setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
        })
        root.addView(TextView(ctx).apply {
            text = "会出：${combo.heroes.joinToString("、") { it.shortName }}"
            setTextColor(0xFF9CD0FF.toInt()); textSize = 14f
            setPadding(0, 10, 0, 30)
        })

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        btnRow.addView(Button(ctx).apply {
            text = "确认点击"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}

                mainHandler.postDelayed({
                    HistoryStore.add(
                        ctx,
                        report.tags.joinToString("\t"),
                        combo.tags.joinToString("\t") + " → " + combo.topLevel + "★ (领袖)"
                    )
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            AutoRunner.autoPick(combo, report.tagRects)
                        } catch (_: Exception) {}
                        mainHandler.post {
                            showToast("已选中：${combo.tags.joinToString("/")}")
                        }
                    }
                }, 200)
            }
        })

        btnRow.addView(Button(ctx).apply {
            text = "取消"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 20
            }
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                showResultPanelWithSuggestions(report, allSuggestions)
            }
        })

        root.addView(btnRow)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // 或者 WRAP_CONTENT 保持原样
            WindowManager.LayoutParams.WRAP_CONTENT, // 或者 MATCH_PARENT 保持原样
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )        .apply { gravity = Gravity.BOTTOM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // ★ 关键：无视所有系统安全区
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }

    private fun showFailDebugPanel(report: AutoRunner.Report2.Failed) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC2A1E1E.toInt())
            setPadding(30, 30, 30, 30)
        }
        root.addView(TextView(ctx).apply {
            text = "❌ 识别失败：${report.reason}"
            setTextColor(0xFFFF8888.toInt()); textSize = 14f
        })
        val ocr = report.ocrAtPos
        if (ocr != null) {
            val sb = StringBuilder()
            sb.append("\nOCR 每位置：\n")
            for (i in 1..5) {
                sb.append("  位置 $i: ${ocr.getOrNull(i) ?: "(未读到)"}\n")
            }
            root.addView(TextView(ctx).apply {
                text = sb.toString()
                setTextColor(0xFFCCCCCC.toInt()); textSize = 12f
                setPadding(0, 8, 0, 8)
            })
        }
        root.addView(Button(ctx).apply {
            text = "关闭"
            setOnClickListener { try { wm.removeView(root) } catch (_: Exception) {} }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // 或者 WRAP_CONTENT 保持原样
            WindowManager.LayoutParams.WRAP_CONTENT, // 或者 MATCH_PARENT 保持原样
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )        .apply { gravity = Gravity.BOTTOM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // ★ 关键：无视所有系统安全区
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                setFitInsetsTypes(0)
            }
        }
        try { wm.addView(root, lp) } catch (_: Exception) {}
    }

    private var colorSamplerOverlay: android.view.View? = null
    private fun showLongPressMenu() {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE1E1E2E.toInt())
            setPadding(40, 40, 40, 40)
        }

        val modeBtn = Button(ctx).apply {
            val isPanel = SettingsStore.mode == "panel"
            text = if (isPanel) "当前：结果面板（点击切换）" else "当前：悬浮球（点击切换）"
            setOnClickListener {
                SettingsStore.mode = if (SettingsStore.mode == "panel") "ball" else "panel"
                val newIsPanel = SettingsStore.mode == "panel"
                text = if (newIsPanel) "当前：结果面板（点击切换）" else "当前：悬浮球（点击切换）"
            }
        }
        root.addView(modeBtn)

        root.addView(Button(ctx).apply {
            text = "全自动模式"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                startFullAuto()
            }
        })

        root.addView(Button(ctx).apply {
            text = "重新校准（招募条件）"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                val intent = Intent(ctx, com.rshock.quanjing.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("recalibrate_full", true)
                }
                ctx.startActivity(intent)
            }
        })

        root.addView(Button(ctx).apply {
            text = "重新校准（自动点位）"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                val intent = Intent(ctx, com.rshock.quanjing.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("recalibrate_auto", true)
                }
                ctx.startActivity(intent)
            }
        })

        root.addView(Button(ctx).apply {
            text = "OCR 视角"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                showOcrPreviewHere()
            }
        })

        root.addView(Button(ctx).apply {
            text = "打开设置"
            setOnClickListener {
                try { wm.removeView(root) } catch (_: Exception) {}
                val intent = Intent(ctx, com.rshock.quanjing.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("open_settings", true)
                }
                ctx.startActivity(intent)
            }
        })

        root.addView(Button(ctx).apply {
            text = "取消"
            setOnClickListener { try { wm.removeView(root) } catch (_: Exception) {} }
        })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        // 找到原来的 val lp = ... 替换为：
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try { wm.addView(root, lp) } catch (_: Exception) {}
    }

    private fun showFullScreenshot() {
        ball?.visibility = View.GONE
        mainHandler.postDelayed({
            CoroutineScope(Dispatchers.Default).launch {
                val bmp = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                if (bmp == null) {
                    mainHandler.post {
                        ball?.visibility = View.VISIBLE
                        showToast("截屏失败")
                    }
                    return@launch
                }

                mainHandler.post {
                    ball?.visibility = View.VISIBLE
                    com.rshock.quanjing.ui.OcrPreview.showFullImage(ctx, bmp) { }
                }
            }
        }, 300)
    }
    private fun showOcrPreviewHere() {
        val r = rois ?: return
        val full = r.full ?: return
        ball?.visibility = View.GONE
        mainHandler.postDelayed({
            CoroutineScope(Dispatchers.Default).launch {
                val bmp: android.graphics.Bitmap? = com.rshock.quanjing.auto.ScreenCaptureService.instance?.capture()
                if (bmp == null) {
                    mainHandler.post {
                        ball?.visibility = View.VISIBLE
                        showToast("截屏失败")
                    }
                    return@launch
                }
                val rects = full.toPixelRects()
                val ocrResults = com.rshock.quanjing.auto.InputTagReader.readRawAtPositions(bmp, rects)
                mainHandler.post {
                    ball?.visibility = View.GONE
                    OcrPreview.show(ctx, bmp, rects, (1..5).map { ocrResults.getOrNull(it) }) {
                        ball?.visibility = View.VISIBLE
                    }
                }
            }
        }, 300)
    }

    private fun showToast(msg: String) {
        // 关掉 Toast，避免遮挡屏幕取色
        android.util.Log.d("QJ_Auto", msg)

        // 原来的代码注释掉：
         mainHandler.post {
            try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() } catch (_: Exception) {}
         }
    }
    fun hide() {
        try { trashView?.let { wm.removeView(it) } } catch (_: Exception) {}
        trashView = null
        try { roiOverlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        roiOverlay = null
        try { ball?.let { wm.removeView(it) } } catch (_: Exception) {}
        ball = null
        setShowing(false)
    }
}