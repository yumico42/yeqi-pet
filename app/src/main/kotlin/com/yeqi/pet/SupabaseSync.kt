package com.yeqi.pet

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSync(private val webView: WebView?) {

    companion object {
        const val SUPABASE_URL = "https://bziuplxscbalmjjmqjzn.supabase.co"
        const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ6aXVwbHhzY2JhbG1qam1xanpuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIzNzMzNjUsImV4cCI6MjA5Nzk0OTM2NX0.Wd1owm6R5bJ9xs5k2-trjeZaDtVCm8Hh7yrqracH1s8"
        const val POLL_INTERVAL = 5000L
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
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Poll failed", e)
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    fun stopPolling() {
        polling = false
        scope.cancel()
    }

    private fun pollState() {
        val url = URL("$SUPABASE_URL/rest/v1/pet_events?order=created_at.desc&limit=5&event_type=eq.state_push")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        handler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onStateUpdate($response)", null
            )
        }
    }

    fun reportGesture(gestureType: String) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("gesture", gestureType)
                    put("timestamp", System.currentTimeMillis())
                }
                val body = JSONObject().apply {
                    put("event_type", gestureType)
                    put("payload", payload)
                }
                postToSupabase("pet_events", body)
                Log.d("SupabaseSync", "Gesture reported: $gestureType")
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Failed to report gesture", e)
            }
        }
    }

    fun reportApp(packageName: String) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("package_name", packageName)
                    put("timestamp", System.currentTimeMillis())
                }
                val body = JSONObject().apply {
                    put("event_type", "app_switch")
                    put("payload", payload)
                }
                postToSupabase("pet_events", body)
                Log.d("SupabaseSync", "App reported: $packageName")
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Failed to report app", e)
            }
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
        val code = conn.responseCode
        Log.d("SupabaseSync", "POST $table -> $code")
        conn.disconnect()
    }
}