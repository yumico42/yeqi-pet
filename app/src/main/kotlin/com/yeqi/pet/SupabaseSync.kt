package com.yeqi.pet

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseSync(private val webView: WebView?) {

    companion object {
        const val SUPABASE_URL = "https://bziuplxscbalmjjmqjzn.supabase.co"
        const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ6aXVwbHhzY2JhbG1qam1xanpuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIzNzMzNjUsImV4cCI6MjA5Nzk0OTM2NX0.Wd1owm6R5bJ9xs5k2-trjeZaDtVCm8Hh7yrqracH1s8"
        const val EVENT_POLL_INTERVAL = 5000L
        const val AI_STATE_POLL_INTERVAL = 300000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var lastAIPushSignature: String? = null

    fun startPolling() {
        if (polling) return
        polling = true
        scope.launch {
            while (polling) {
                try {
                    pollEventState()
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "Event poll failed", e)
                }
                delay(EVENT_POLL_INTERVAL)
            }
        }
        scope.launch {
            while (polling) {
                try {
                    pollAIPushState()
                } catch (e: Exception) {
                    Log.e("SupabaseSync", "AI state poll failed", e)
                }
                delay(AI_STATE_POLL_INTERVAL)
            }
        }
    }

    fun stopPolling() {
        polling = false
        scope.cancel()
    }

    private fun pollEventState() {
        val response = getFromSupabase("pet_events?order=created_at.desc&limit=5&event_type=eq.state_push")

        handler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onStateUpdate($response)", null
            )
        }
    }

    private fun pollAIPushState() {
        val response = try {
            getFromSupabase("pet_state?select=*&order=updated_at.desc&limit=20")
        } catch (e: Exception) {
            getFromSupabase("pet_state?select=*&limit=20")
        }
        if (response == lastAIPushSignature) return

        val payload = buildAIPushPayload(response) ?: return
        lastAIPushSignature = response

        handler.post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onAIPush && window.petEngine.onAIPush($payload)",
                null
            )
        }
    }

    private fun getFromSupabase(path: String): String {
        val url = URL("$SUPABASE_URL/rest/v1/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        val response = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return response
    }

    private fun buildAIPushPayload(response: String): JSONObject? {
        val rows = JSONArray(response)
        if (rows.length() == 0) return null

        val payload = JSONObject()
        val first = rows.optJSONObject(0)
        if (first != null) {
            copyPushFields(first, payload)
            first.optJSONObject("payload")?.let { copyPushFields(it, payload) }
        }

        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val key = row.optString("state_key", row.optString("key", ""))
            if (key.isBlank()) continue
            val value = when {
                row.has("state_value") -> row.opt("state_value")
                row.has("value") -> row.opt("value")
                else -> null
            } ?: continue
            when (key) {
                "mood" -> payload.put("mood", value)
                "bubble", "speech_bubble" -> payload.put("bubble", value)
                "expression" -> payload.put("expression", value)
            }
        }

        return if (payload.length() > 0) payload else null
    }

    private fun copyPushFields(source: JSONObject, target: JSONObject) {
        listOf("mood", "bubble", "speech_bubble", "expression").forEach { key ->
            if (source.has(key)) target.put(key, source.opt(key))
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
