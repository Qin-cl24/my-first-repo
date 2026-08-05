package com.epinoia.deskpet

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class PetOverlayService : Service() {
    private var webView: WebView? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Toast.makeText(this, "Epinoia 桌宠正在启动...", Toast.LENGTH_SHORT).show()
        createOverlay()
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        webView = WebView(this).apply {
            webViewClient = WebViewClient()
            // 这里先加载一个简单测试页面，后面再替换成 pet.html
            loadData("<html><body style='background:transparent;color:white;font-size:20px;'>🧡 Epinoia<br>加载中...</body></html>", "text/html; charset=utf-8", "UTF-8")
            settings.javaScriptEnabled = true
            isClickable = true
            setBackgroundColor(0x00000000) // 透明背景
        }

        val params = WindowManager.LayoutParams(
            300, 300,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50  // 距离左边 50px
            y = 200 // 距离顶部 200px
        }

        try {
            windowManager?.addView(webView, params)
            Toast.makeText(this, "桌宠已上线！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.let { windowManager?.removeView(it) }
        webView = null
    }
}
