package com.epinoia.deskpet

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
        private const val PET_WIDTH_DP = 64
        private const val PET_HEIGHT_DP = 64
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 10
        private const val IDLE_TIMEOUT_MS = 10_000L      // 无操作多久后自动贴边
        private const val DOCK_EDGE_MARGIN_DP = 16       // 贴边时留在屏内的边缘(可触摸拉回)
        private const val DOCKED_ALPHA = 0.5f            // 贴边半透明度
        private const val DOCK_ANIM_MS = 300L
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
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        try {
            windowManager?.addView(overlayView, params)
            resetIdleTimer()
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
    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { dockToEdge() }

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (docked) undock()
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
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> onDoubleTap()
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                onTap()
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

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels

    // === 悬浮球：无操作自动贴边 + 半透明 ===
    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, IDLE_TIMEOUT_MS)
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
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
