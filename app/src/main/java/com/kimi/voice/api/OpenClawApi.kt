package com.kimi.voice.api

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenClawApi(private val context: Context) {
    
    private val client: OkHttpClient
    private val gson = Gson()
    
    // ⚠️ KONFIGURIERE HIER DEINEN OPENCLAW GATEWAY
    private val gatewayUrl: String
        get() = context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE)
            .getString("gateway_url", "http://192.168.1.100:8080") ?: "http://192.168.1.100:8080"
    
    private val gatewayToken: String
        get() = context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE)
            .getString("gateway_token", "") ?: ""
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    data class CommandRequest(
        val text: String,
        val source: String = "voice_assistant",
        val deviceId: String = "android"
    )
    
    data class CommandResponse(
        val text: String,
        val actions: List<Action> = emptyList()
    )
    
    data class Action(
        val type: String,
        val payload: Map<String, Any> = emptyMap()
    )
    
    suspend fun sendCommand(command: String): Result<CommandResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = gson.toJson(CommandRequest(text = command))
            
            val request = Request.Builder()
                .url("$gatewayUrl/api/v1/command")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $gatewayToken")
                .header("X-Source", "kimi-voice-android")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                
                // Parse die Antwort
                val jsonResponse = gson.fromJson(body, JsonObject::class.java)
                val text = jsonResponse.get("text")?.asString ?: "Keine Antwort erhalten"
                
                Result.success(CommandResponse(text = text))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Fallback: Sende einfach eine Nachricht
    suspend fun sendMessage(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("message", message)
                .add("source", "android_voice")
                .build()
            
            val request = Request.Builder()
                .url("$gatewayUrl/webhook/command")
                .post(formBody)
                .header("Authorization", "Bearer $gatewayToken")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body?.string() ?: "OK")
                } else {
                    Result.failure(IOException("HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}