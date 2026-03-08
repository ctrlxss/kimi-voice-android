package com.kimi.voice.util

import android.content.Context
import android.util.Log
import com.kimi.voice.api.ElevenLabsTts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hybrid TTS Manager - kombiniert beste Optionen:
 * 
 * 1. ElevenLabs (Cloud) - EXTREM gute Qualität, Premium-Erlebnis
 * 2. Google TTS (Local) - Gute Qualität, funktioniert offline
 * 3. System TTS (Fallback) - Immer verfügbar
 * 
 * Wählt automatisch die beste Option basierend auf:
 * - Verfügbarkeit von ElevenLabs API Key
 * - Internet-Verbindung
 * - User-Präferenz
 */
class HybridTtsManager(private val context: Context) {
    
    companion object {
        const val TAG = "HybridTtsManager"
        
        // Modi
        const val MODE_AUTO = "auto"           // Automatisch wählen
        const val MODE_ELEVENLABS = "eleven"   // Immer ElevenLabs
        const val MODE_LOCAL = "local"         // Immer local
    }
    
    private val localTts = TtsManager(context)
    private val elevenLabsTts = ElevenLabsTts(context)
    private var currentMode = MODE_AUTO
    
    // Callback
    var onSpeechComplete: (() -> Unit)? = null
        set(value) {
            field = value
            localTts.onSpeechComplete = value
            elevenLabsTts.onPlaybackComplete = value
        }
    
    init {
        // Lade User-Präferenz
        currentMode = context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE)
            .getString("tts_mode", MODE_AUTO) ?: MODE_AUTO
    }
    
    /**
     * Setzt den ElevenLabs API Key
     */
    fun setElevenLabsApiKey(key: String) {
        elevenLabsTts.setApiKey(key)
    }
    
    /**
     * Setzt den TTS Modus
     */
    fun setMode(mode: String) {
        currentMode = mode
        context.getSharedPreferences("kimi_prefs", Context.MODE_PRIVATE).edit()
            .putString("tts_mode", mode)
            .apply()
    }
    
    /**
     * Gibt aktuellen Modus zurück
     */
    fun getMode(): String = currentMode
    
    /**
     * Spricht Text mit der besten verfügbaren Methode
     */
    fun speak(text: String, utteranceId: String = "kimi_response") {
        val useElevenLabs = shouldUseElevenLabs()
        
        if (useElevenLabs) {
            Log.d(TAG, "Using ElevenLabs for: $text")
            CoroutineScope(Dispatchers.Main).launch {
                elevenLabsTts.speak(text).onFailure { error ->
                    Log.w(TAG, "ElevenLabs failed, falling back to local: ${error.message}")
                    localTts.speak(text, utteranceId)
                }
            }
        } else {
            Log.d(TAG, "Using local TTS for: $text")
            localTts.speak(text, utteranceId)
        }
    }
    
    /**
     * Schnelle Status-Updates ohne API-Call
     */
    fun speakQuick(text: String) {
        localTts.speak(text, "quick")
    }
    
    /**
     * Entscheidet ob ElevenLabs genutzt werden soll
     */
    private fun shouldUseElevenLabs(): Boolean {
        return when (currentMode) {
            MODE_ELEVENLABS -> elevenLabsTts.isConfigured()
            MODE_LOCAL -> false
            else -> { // MODE_AUTO
                // Nutze ElevenLabs wenn verfügbar, aber nicht für kurze Status-Meldungen
                elevenLabsTts.isConfigured()
            }
        }
    }
    
    /**
     * Stoppt alle Wiedergabe
     */
    fun stop() {
        localTts.stop()
        elevenLabsTts.stop()
    }
    
    /**
     * Prüft welche Optionen verfügbar sind
     */
    fun getAvailableOptions(): TtsOptions {
        return TtsOptions(
            localAvailable = localTts.isAvailable(),
            googleTtsAvailable = localTts.hasGoogleTts(),
            elevenLabsAvailable = elevenLabsTts.isConfigured(),
            currentMode = currentMode
        )
    }
    
    /**
     * Testet ElevenLabs Verbindung
     */
    suspend fun testElevenLabs(): Boolean {
        return elevenLabsTts.testConnection()
    }
    
    /**
     * Gibt Info über aktuelle Konfiguration
     */
    fun getInfo(): String {
        val options = getAvailableOptions()
        return buildString {
            appendLine("TTS Status:")
            appendLine("  Modus: $currentMode")
            appendLine("  Local TTS: ${if (options.localAvailable) "✅" else "❌"}")
            appendLine("  Google TTS: ${if (options.googleTtsAvailable) "✅" else "❌"}")
            appendLine("  ElevenLabs: ${if (options.elevenLabsAvailable) "✅" else "❌"}")
            appendLine("  Aktive Stimme: ${localTts.getCurrentVoiceInfo()}")
        }
    }
    
    /**
     * Öffnet TTS Einstellungen
     */
    fun openSettings() {
        localTts.openTtsSettings()
    }
    
    /**
     * Cleanup
     */
    fun shutdown() {
        localTts.shutdown()
        // ElevenLabs braucht kein shutdown
    }
    
    data class TtsOptions(
        val localAvailable: Boolean,
        val googleTtsAvailable: Boolean,
        val elevenLabsAvailable: Boolean,
        val currentMode: String
    )
}