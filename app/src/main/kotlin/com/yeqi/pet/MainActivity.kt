package com.yeqi.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
            text = if (Settings.canDrawOverlays(this@MainActivity))
                "✅ 悬浮窗权限已授权" else "❌ 需要悬浮窗权限"
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

        val btnStart = Button(this).apply {
            text = "启动夜栖"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE) {
            recreate()
        }
    }
}
