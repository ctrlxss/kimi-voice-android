package com.kimi.voice.api

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs TTS - Premium Cloud-basierte Sprachsynthese
 * EXTREM gute Qualität, aber braucht API Key und Internet
 * 
 * Kosten: ~$5/Monat für 100k Zeichen (sehr günstig)
 * Website: https://elevenlabs.io
 */
class ElevenLabsTts(private val context: Context) {
    
    companion object {
        const val TAG = "ElevenLabsTts"
        const val BASE_URL = "https://api.elevenlabs.io/v1"
        
        // Voice IDs - diese sind sehr gut für Deutsch:
        const val VOICE_BELLA = "XB0fDUnXU5powFXDhCwa"      // Weiblich, warm
        const val VOICE_RACHEL = "21m00Tcm4TlvDq8ikWAM"     // Weiblich, klar
        const val VOICE_DOMI = "AZnzlk1XvdvUeBnXmlld"       // Weiblich, energisch
        const val VOICE_ANTONI = "ErXwobaYiN019PkySvjV"     // Männlich
        const val VOICE_JOSH = "TxGEqnHWrfWFTfGW9XjX"       // Männlich, tief
    }
    
    private val client: OkHttpClient
    private var apiKey: String = ""
    private var currentVoiceId: String = VOICE_BELLA
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    
    var onPlaybackComplete: (() -> Unit)? = null
    
    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // Länger wegen Audio-Generierung
            .build()
        
        // Lade API Key aus Preferences
        apiKey = context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE)
            .getString("elevenlabs_api_key", "") ?: ""
    }
    
    /**
     * Setzt den API Key
     */
    fun setApiKey(key: String) {
        apiKey = key
        context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE).edit()
            .putString("elevenlabs_api_key", key)
            .apply()
    }
    
    /**
     * Setzt die Stimme
     */
    fun setVoice(voiceId: String) {
        currentVoiceId = voiceId
    }
    
    /**
     * Spricht Text mit ElevenLabs
     */
    suspend fun speak(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IOException("ElevenLabs API Key nicht gesetzt"))
        }
        
        try {
            // 1. Audio generieren
            val audioFile = generateSpeech(text)
            
            if (audioFile == null) {
                return@withContext Result.failure(IOException("Audio Generierung fehlgeschlagen"))
            }
            
            // 2. Abspielen
            withContext(Dispatchers.Main) {
                playAudio(audioFile)
            }
            
            Result.success("OK")
            
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs error: ${e.message}")
            Result.failure(e)
        }
    }
    
    private suspend fun generateSpeech(text: String): File? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/text-to-speech/$currentVoiceId"
        
        val requestBody = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2") // Bestes Modell für Deutsch
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
                put("style", 0.0)
                put("use_speaker_boost", true)
            })
        }
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .header("xi-api-key", apiKey)
            .header("Content-Type", "application/json")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "ElevenLabs API error: ${response.code} - $errorBody")
                return@withContext null
            }
            
            // Speichere Audio
            val audioBytes = response.body?.bytes()
            if (audioBytes == null) {
                return@withContext null
            }
            
            val tempFile = File(context.cacheDir, "kimi_tts_${System.currentTimeMillis()}.mp3")
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            
            Log.d(TAG, "Audio saved: ${tempFile.absolutePath} (${audioBytes.size} bytes)")
            return@withContext tempFile
        }
    }
    
    private fun playAudio(file: File) {
        try {
            stop() // Stoppe aktuelle Wiedergabe
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    isPlaying = true
                }
                setOnCompletionListener { mp ->
                    isPlaying = false
                    mp.release()
                    onPlaybackComplete?.invoke()
                    // Lösche temporäre Datei
                    file.delete()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    isPlaying = false
                    mp.release()
                    onPlaybackComplete?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback error: ${e.message}")
            isPlaying = false
            onPlaybackComplete?.invoke()
        }
    }
    
    fun stop() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.release()
            }
            mediaPlayer = null
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }
    
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * Prüft ob API Key gesetzt ist
     */
    fun isConfigured(): Boolean = apiKey.isNotEmpty()
    
    /**
     * Testet die Verbindung
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext false
        
        try {
            val request = Request.Builder()
                .url("$BASE_URL/voices")
                .header("xi-api-key", apiKey)
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Lädt verfügbare Stimmen
     */
    suspend fun getVoices(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) return@withContext emptyList()
        
        try {
            val request = Request.Builder()
                .url("$BASE_URL/voices")
                .header("xi-api-key", apiKey)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                
                val json = JSONObject(response.body?.string() ?: "{}")
                val voicesArray = json.getJSONArray("voices")
                val voices = mutableListOf<VoiceInfo>()
                
                for (i in 0 until voicesArray.length()) {
                    val voice = voicesArray.getJSONObject(i)
                    voices.add(VoiceInfo(
                        id = voice.getString("voice_id"),
                        name = voice.getString("name"),
                        previewUrl = voice.optString("preview_url", "")
                    ))
                }
                
                voices
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching voices: ${e.message}")
            emptyList()
        }
    }
    
    data class VoiceInfo(
        val id: String,
        val name: String,
        val previewUrl: String
    )
}