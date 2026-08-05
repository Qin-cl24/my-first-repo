package com.epinoia.deskpet

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启动悬浮窗服务
        startService(android.content.Intent(this, PetOverlayService::class.java))
        
        // 主 Activity 直接关闭（我们只需要后台服务）
        finish()
    }

