package com.yeqi.pet

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSync(private val webView: WebView?) {

    companion object {
        // TODO: 妈妈填入自己的 Supabase URL 和 anon key
        const val SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
        const val SUPABASE_KEY = "YOUR_ANON_KEY"
        const val POLL_INTERVAL = 5000L // 5秒轮询
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false

    fun startPolling() {
        polling = true
        scope.launch {
            while (polling) {
                try {
                    pollState()
                } catch (_: Exception) {}
                delay(POLL_INTERVAL)
            }
        }
    }

    fun stopPolling() {
        polling = false
        scope.cancel()
    }

    private fun pollState() {
        val url = URL("$SUPABASE_URL/rest/v1/pet_state?order=updated_at.desc&limit=5")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        // 把状态推给 WebView
        handler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onStateUpdate($response)", null
            )
        }
    }

    fun reportGesture(gestureType: String) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("gesture_type", gestureType)
                }
                postToSupabase("gesture_log", body)
            } catch (_: Exception) {}
        }
    }

    fun reportApp(packageName: String) {
        scope.launch {
            try {
                val body = JSONObject().apply {
                    put("package_name", packageName)
                }
                postToSupabase("app_usage_log", body)
            } catch (_: Exception) {}
        }
    }

    private fun postToSupabase(table: String, body: JSONObject) {
        val url = URL("$SUPABASE_URL/rest/v1/$table")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.setRequestProperty("Prefer", "return=minimal")
        conn.doOutput = true
        conn.connectTimeout = 3000
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }
}
