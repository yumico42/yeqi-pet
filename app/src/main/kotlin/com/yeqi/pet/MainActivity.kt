package com.yeqi.pet

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderContent()
    }

    override fun onResume() {
        super.onResume()
        renderContent()
    }

    private fun renderContent() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 128, 64, 64)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "\uD83D\uDC3E 夜栖桌宠"
            textSize = 28f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val status = TextView(this).apply {
            text = buildPermissionStatus()
            textSize = 16f
            setPadding(0, 0, 0, 48)
        }
        layout.addView(status)

        val btnPermission = Button(this).apply {
            text = "授权悬浮窗权限"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_REQUEST_CODE)
            }
        }
        layout.addView(btnPermission)

        val btnUsage = Button(this).apply {
            text = "开启使用情况权限"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        layout.addView(btnUsage)

        val btnNotification = Button(this).apply {
            text = "开启通知监听权限"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        layout.addView(btnNotification)

        val btnStart = Button(this).apply {
            text = "启动夜栖"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startForegroundService(Intent(this@MainActivity, OverlayService::class.java))
                    Toast.makeText(this@MainActivity, "夜栖趴上去了！", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "请先授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(btnStart)

        val btnStop = Button(this).apply {
            text = "收回夜栖"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(this@MainActivity, "夜栖回窝了", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btnStop)

        setContentView(layout)
    }

    private fun buildPermissionStatus(): String {
        val overlay = if (Settings.canDrawOverlays(this)) "✅ 悬浮窗权限已授权" else "❌ 需要悬浮窗权限"
        val usage = if (hasUsageStatsPermission()) "✅ 使用情况权限已授权" else "❌ 需要使用情况权限"
        val notification = if (hasNotificationListenerPermission()) "✅ 通知监听已授权" else "❌ 需要通知监听权限"
        return listOf(overlay, usage, notification).joinToString("\n")
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasNotificationListenerPermission(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE) {
            recreate()
        }
    }
}
