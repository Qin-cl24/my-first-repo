package com.epinoia.deskpet

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("deskpet_prefs", MODE_PRIVATE)

        findViewById<Button>(R.id.btn_overlay_perm).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btn_notif_perm).setOnClickListener { requestNotif() }
        findViewById<Button>(R.id.btn_storage_perm).setOnClickListener { requestStorage() }

        bindSwitch(R.id.sw_dock, "pref_dock", true)
        bindSwitch(R.id.sw_edge, "pref_edge_run", true)
        bindSwitch(R.id.sw_peek, "pref_peekaboo", true)
        bindSwitch(R.id.sw_actions, "pref_idle_actions", true)
        bindSwitch(R.id.sw_water, "pref_water", true)
        bindSwitch(R.id.sw_keyboard, "pref_keyboard", true)
        bindSwitch(R.id.sw_ai, "pref_ai_channel", true)
        bindSwitch(R.id.sw_http, "pref_local_http", true)

        // AI 服务配置（可空 = 不连任何 AI，保护私有）
        val etUrl = findViewById<EditText>(R.id.et_ai_url)
        val etKey = findViewById<EditText>(R.id.et_ai_key)
        etUrl.setText(prefs.getString("pref_ai_url", ""))
        etKey.setText(prefs.getString("pref_ai_key", ""))
        findViewById<Button>(R.id.btn_save_ai).setOnClickListener {
            prefs.edit()
                .putString("pref_ai_url", etUrl.text.toString().trim())
                .putString("pref_ai_key", etKey.text.toString().trim())
                .apply()
            Toast.makeText(this, "AI 配置已保存（重启桌宠生效）", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限！", Toast.LENGTH_LONG).show()
                requestOverlay()
            } else {
                val i = Intent(this, PetOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i)
                else startService(i)
                Toast.makeText(this, "桌宠启动中...", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            stopService(Intent(this, PetOverlayService::class.java))
            Toast.makeText(this, "桌宠已停止", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermButtons()
    }

    private fun bindSwitch(id: Int, key: String, def: Boolean) {
        val sw = findViewById<Switch>(id)
        sw.isChecked = prefs.getBoolean(key, def)
        sw.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
    }

    private fun refreshPermButtons() {
        findViewById<Button>(R.id.btn_overlay_perm).text =
            if (Settings.canDrawOverlays(this)) "✅ 悬浮窗权限已授予" else "⚠️ 悬浮窗权限（必需）"
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        findViewById<Button>(R.id.btn_notif_perm).text =
            if (notifOk) "✅ 通知权限已授予" else "⚠️ 通知权限（保活用）"
        val storageOk = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        findViewById<Button>(R.id.btn_storage_perm).text =
            if (storageOk) "✅ 文件访问权限已授予" else "⚠️ 文件访问权限（AI 消息通道用）"
    }

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun requestNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
