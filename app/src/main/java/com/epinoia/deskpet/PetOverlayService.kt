package com.epinoia.deskpet

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.NotificationCompat

class PetOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val CHANNEL_ID = "deskpet_overlay"
        private const val NOTIFICATION_ID = 1
        private const val PET_WIDTH_DP = 72
        private const val PET_HEIGHT_DP = 72
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 10
        private const val IDLE_TIMEOUT_MS = 10_000L      // 无操作多久后自动贴边
        private const val DOCK_EDGE_MARGIN_DP = 16       // 贴边时留在屏内的边缘(可触摸拉回)
        private const val DOCKED_ALPHA = 0.5f            // 贴边半透明度
        private const val DOCK_ANIM_MS = 300L
        private const val LONELY_INTERVAL_MS = 5 * 60 * 1000L  // 孤独递进每 5 分钟一级
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        Toast.makeText(this, "Epinoia 桌宠正在启动...", Toast.LENGTH_SHORT).show()
        setupOverlay()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌宠悬浮窗", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🧡 Epinoia 桌宠")
            .setContentText("点击返回应用")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_WIDTH_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(20)
            y = dpToPx(250)
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000) // 透明背景
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(this, "AndroidBridge")
            // 键盘感知：Android 12+ 全局 IME insets，悬浮窗也能收到
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { v, insets ->
                    if (prefBool("pref_keyboard", true)) {
                        val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                        if (ime.bottom > 0 && !keyboardVisible) {
                            keyboardVisible = true
                            imeHeightPx = ime.bottom
                            onKeyboardShow()
                        } else if (ime.bottom == 0 && keyboardVisible) {
                            keyboardVisible = false
                            imeHeightPx = 0
                            onKeyboardHide()
                        }
                    }
                    insets
                }
            }
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        try {
            windowManager?.addView(overlayView, params)
            resetIdleTimer()
            resetLonelyTimer()
            scheduleEdgeRun()
            schedulePeekaboo()
            scheduleAiWatch()
            Toast.makeText(this, "桌宠已上线！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // === 手势系统 (参考 AI-Live-Overflow) ===

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var docked = false
    private var dockSide = -1
    private var tapCount = 0
    private var firstTapTime = 0L
    private var lonelyStage = 0
    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { dockToEdge() }
    private val lonelyRunnable = Runnable { advanceLoneliness() }
    private val edgeRunHandler = Handler(Looper.getMainLooper())
    private var edgeAnimator: ValueAnimator? = null
    private val peekabooHandler = Handler(Looper.getMainLooper())
    private var keyboardVisible = false
    private var imeHeightPx = 0
    private var posBeforeKeyboardY = 0
    private val aiHandler = Handler(Looper.getMainLooper())
    private var aiLastContent = ""
    private var aiLinkLast = ""

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (docked) undock()
                    // 任何互动都唤醒 + 重置孤独进度 + 打断/重置边缘跑
                    overlayView?.evaluateJavascript("window.wakeUp && window.wakeUp()", null)
                    resetLonelyTimer()
                    edgeAnimator?.cancel()
                    scheduleEdgeRun()
                    schedulePeekaboo()
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    resetIdleTimer()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > MOVE_THRESHOLD || Math.abs(dy) > MOVE_THRESHOLD) {
                        hasMoved = true
                        params?.let { p ->
                            // 始终限制在屏幕可视范围内，避免拖丢/拉不回来
                            p.x = (initialX + dx).coerceIn(0, screenW - p.width)
                            p.y = (initialY + dy).coerceIn(0, screenH - p.height)
                            windowManager?.updateViewLayout(overlayView, p)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    resetIdleTimer()
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        val now = System.currentTimeMillis()
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> { tapCount = 0; onLongPress() }
                            else -> {
                                // 连击计数：2 秒窗口内累计 3/5/8 次触发递进反应
                                if (now - firstTapTime > 2000) { tapCount = 0; firstTapTime = now }
                                if (tapCount == 0) firstTapTime = now
                                tapCount++
                                when (tapCount) {
                                    2 -> onDoubleTap()
                                    3 -> onCombo3()
                                    5 -> onCombo5()
                                    8 -> { onCombo8(); tapCount = 0 }
                                    else -> onTap()
                                }
                            }
                        }
                    } else {
                        // 快速甩动 vs 普通拖拽
                        val dx = (event.rawX - initialTouchX)
                        val dy = (event.rawY - initialTouchY)
                        val velocity = Math.sqrt((dx * dx + dy * dy).toDouble())
                        if (velocity > 200 && elapsed < 400) onFling() else onDragEnd()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onTap()", null)
    }

    private fun onDoubleTap() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onDoubleTap()", null)
    }

    private fun onLongPress() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onLongPress()", null)
    }

    private fun onDragEnd() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onDragEnd()", null)
    }

    private fun onFling() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onFling()", null)
    }

    private fun onCombo3() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onCombo3()", null)
    }

    private fun onCombo5() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onCombo5()", null)
    }

    private fun onCombo8() {
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onCombo8()", null)
    }

    // === 孤独递进：无互动 5/10/15/20 分钟 → 偷看/吹泡泡/打瞌睡/睡着 ===
    private fun resetLonelyTimer() {
        lonelyStage = 0
        handler.removeCallbacks(lonelyRunnable)
        handler.postDelayed(lonelyRunnable, LONELY_INTERVAL_MS)
    }

    private fun advanceLoneliness() {
        if (lonelyStage >= 4) return  // 已睡着，保持
        lonelyStage++
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onLonely$lonelyStage()", null
        )
        if (lonelyStage < 4) handler.postDelayed(lonelyRunnable, LONELY_INTERVAL_MS)
    }

    // === 边缘跑：随机沿屏幕边缘滑跑一段，然后贴边 ===
    private fun scheduleEdgeRun() {
        edgeRunHandler.removeCallbacksAndMessages(null)
        if (!prefBool("pref_edge_run", true)) return
        edgeRunHandler.postDelayed({
            edgeRun()
            scheduleEdgeRun()
        }, 30000L + (Math.random() * 30000L).toLong())  // 30~60 秒随机
    }

    private fun edgeRun() {
        val p = params ?: return
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onEdgeRun()", null)
        // 跑动时恢复不透明
        p.alpha = 1f
        overlayView?.let { windowManager?.updateViewLayout(it, p) }
        val w = p.width
        val edge = dpToPx(DOCK_EDGE_MARGIN_DP)
        val nearLeft = p.x < (screenW - w) / 2
        val edgeX = if (nearLeft) -w + edge else screenW - edge
        // 1) 先跑到边缘
        animateXTo(edgeX, 450L) {
            // 2) 沿边缘上下跑一段
            val toY = if (p.y < screenH / 2) screenH - p.height - dpToPx(60)
                      else dpToPx(60)
            animateYTo(toY, 1600L) {
                // 3) 停住贴边半透明
                docked = true
                dockSide = if (nearLeft) -1 else 1
                animateTo(p.x, DOCKED_ALPHA)
            }
        }
    }

    private fun animateXTo(target: Int, dur: Long, onEnd: () -> Unit = {}) {
        val p = params ?: return
        val start = p.x
        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = dur
            addUpdateListener {
                params?.let { pr ->
                    pr.x = it.animatedValue as Int
                    overlayView?.let { v -> windowManager?.updateViewLayout(v, pr) }
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }

    private fun animateYTo(target: Int, dur: Long, onEnd: () -> Unit = {}) {
        val p = params ?: return
        val start = p.y
        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = dur
            addUpdateListener {
                params?.let { pr ->
                    pr.y = it.animatedValue as Int
                    overlayView?.let { v -> windowManager?.updateViewLayout(v, pr) }
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }

    // === JS 桥：桌宠做小动作时从贴边弹出，刷存在感 ===
    @android.webkit.JavascriptInterface
    fun peekOut() {
        if (docked) undock()
        resetIdleTimer()
    }

    // === JS 桥：读取设置开关 ===
    @android.webkit.JavascriptInterface
    fun getPref(key: String, def: Boolean): Boolean =
        getSharedPreferences("deskpet_prefs", MODE_PRIVATE).getBoolean(key, def)

    // === 躲猫猫：随机消失 → 随机位置突然出现 ===
    private fun schedulePeekaboo() {
        peekabooHandler.removeCallbacksAndMessages(null)
        if (!prefBool("pref_peekaboo", true)) return
        peekabooHandler.postDelayed({
            peekaboo()
            schedulePeekaboo()
        }, 3 * 60 * 1000L + (Math.random() * 4 * 60 * 1000L).toLong())  // 3~7 分钟随机
    }

    private fun peekaboo() {
        val p = params ?: return
        // 先淡出消失
        animateAlpha(0f, 250L) {
            // 随机出现在屏幕内任意位置
            val w = p.width; val h = p.height
            p.x = (Math.random() * (screenW - w)).toInt()
            p.y = (Math.random() * (screenH - h)).toInt()
            overlayView?.let { windowManager?.updateViewLayout(it, p) }
            overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onPeekaboo()", null)
            // 淡入现身
            animateAlpha(1f, 250L)
        }
    }

    private fun animateAlpha(target: Float, dur: Long, onEnd: () -> Unit = {}) {
        val p = params ?: return
        val start = p.alpha
        ValueAnimator.ofFloat(start, target).apply {
            duration = dur
            addUpdateListener {
                params?.let { pr ->
                    pr.alpha = it.animatedValue as Float
                    overlayView?.let { v -> windowManager?.updateViewLayout(v, pr) }
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { onEnd() }
            })
            start()
        }
    }

    // === 键盘感知：弹出时趴到键盘上方，收起时回原位 ===
    private fun onKeyboardShow() {
        val p = params ?: return
        posBeforeKeyboardY = p.y
        if (docked) undock()
        // 移到键盘正上方（键盘顶边之上，不挡按键）
        val targetY = (screenH - imeHeightPx - p.height).coerceAtLeast(0)
        p.y = targetY
        overlayView?.let { windowManager?.updateViewLayout(it, p) }
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onKeyboardUp()", null)
    }

    private fun onKeyboardHide() {
        params?.let { p ->
            p.y = posBeforeKeyboardY
            overlayView?.let { windowManager?.updateViewLayout(it, p) }
        }
        overlayView?.evaluateJavascript("window.petEngine && window.petEngine.onKeyboardHide()", null)
    }

    // === AI 消息通道：监听 /sdcard/Deskpet/messages.txt（格式：动作名|文本） ===
    private fun scheduleAiWatch() {
        aiHandler.removeCallbacksAndMessages(null)
        aiHandler.postDelayed(aiWatchRunnable, 2000)
    }

    private val aiWatchRunnable = Runnable { watchAiMessages() }

    private fun watchAiMessages() {
        if (prefBool("pref_ai_channel", true)) {
            try {
                val f = java.io.File("/sdcard/Deskpet/messages.txt")
                if (f.exists() && f.canRead()) {
                    val content = f.readText().trim()
                    if (content.isNotEmpty() && content != aiLastContent) {
                        aiLastContent = content
                        handleAiMessage(content)
                    }
                }
            } catch (e: Exception) {
                // 文件读取失败（权限等）静默跳过
            }
        }
        pollAiLink()
        aiHandler.postDelayed(aiWatchRunnable, 2000)
    }

    // AI 服务链接（可选）：配置了才轮询，默认空 = 不连任何 AI（保护私有）
    private fun pollAiLink() {
        val p = getSharedPreferences("deskpet_prefs", MODE_PRIVATE)
        val url = p.getString("pref_ai_url", "") ?: ""
        if (url.isBlank()) return
        Thread {
            try {
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    val key = p.getString("pref_ai_key", "") ?: ""
                    if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText().trim()
                    if (body.isNotEmpty() && body != aiLinkLast) {
                        aiLinkLast = body
                        aiHandler.post { handleAiMessage(body) }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                // 网络失败静默
            }
        }.start()
    }

    private fun handleAiMessage(content: String) {
        val parts = content.split("|", limit = 2)
        val action = parts[0].trim()
        val text = if (parts.size > 1) parts[1].trim() else action
        val js = if (parts.size > 1)
            "window.petEngine && window.petEngine.onAiSay(${escapeJs(text)}, '${escapeJs(action)}')"
        else
            "window.petEngine && window.petEngine.onAiSay(${escapeJs(text)}, 'none')"
        overlayView?.evaluateJavascript(js, null)
        if (docked) undock()  // 说话时弹出来刷存在感
    }

    private fun escapeJs(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    // === 悬浮球：无操作自动贴边 + 半透明 ===
    private fun prefBool(key: String, def: Boolean): Boolean =
        getSharedPreferences("deskpet_prefs", MODE_PRIVATE).getBoolean(key, def)

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        if (prefBool("pref_dock", true)) handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
    }

    private fun dockToEdge() {
        val p = params ?: return
        val w = p.width
        val edge = dpToPx(DOCK_EDGE_MARGIN_DP)
        val nearLeft = p.x < (screenW - w) / 2
        val targetX = if (nearLeft) -w + edge else screenW - edge
        dockSide = if (nearLeft) -1 else 1
        docked = true
        animateTo(targetX, DOCKED_ALPHA)
    }

    private fun undock() {
        if (!docked) return
        docked = false
        params?.let { p ->
            // 一碰就弹回屏内（贴左回左缘、贴右回右缘），立即响应触摸
            p.x = if (dockSide < 0) 0 else screenW - p.width
            p.alpha = 1f
            overlayView?.let { windowManager?.updateViewLayout(it, p) }
        }
    }

    private fun animateTo(targetX: Int, targetAlpha: Float) {
        val p = params ?: return
        val startX = p.x
        val startAlpha = p.alpha
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DOCK_ANIM_MS
            addUpdateListener {
                val f = it.animatedValue as Float
                params?.let { pr ->
                    pr.x = startX + ((targetX - startX) * f).toInt()
                    pr.alpha = startAlpha + (targetAlpha - startAlpha) * f
                    overlayView?.let { v -> windowManager?.updateViewLayout(v, pr) }
                }
            }
            start()
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacks(idleRunnable)
        handler.removeCallbacks(lonelyRunnable)
        edgeRunHandler.removeCallbacksAndMessages(null)
        peekabooHandler.removeCallbacksAndMessages(null)
        aiHandler.removeCallbacksAndMessages(null)
        edgeAnimator?.cancel()
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
