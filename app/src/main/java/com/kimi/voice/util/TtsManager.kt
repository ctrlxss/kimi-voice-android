package com.kimi.voice.util

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.*

/**
 * Verbesserter TTS Manager mit mehreren Engine-Optionen
 * 
 * Priorität:
 * 1. Google TTS (beste Qualität, falls installiert)
 * 2. Samsung TTS (gut auf Samsung Geräten)
 * 3. System Default
 */
class TtsManager(private val context: Context) {
    
    companion object {
        const val TAG = "TtsManager"
        
        // Google TTS Package
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val SAMSUNG_TTS_PACKAGE = "com.samsung.SMT"
    }
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var pendingUtterances = mutableListOf<Pair<String, String>>()
    private var preferredEngine: String? = null
    
    // Callback für wenn Sprache fertig ist
    var onSpeechComplete: (() -> Unit)? = null
    
    init {
        detectBestEngine()
        initTts()
    }
    
    /**
     * Findet die beste verfügbare TTS Engine
     */
    private fun detectBestEngine() {
        val engines = TextToSpeech.getEngines()
        
        Log.d(TAG, "Available TTS engines:")
        engines.forEach { engine ->
            Log.d(TAG, "  - ${engine.name} (${engine.label})")
        }
        
        // Priorität: Google > Samsung > Erste verfügbare
        preferredEngine = when {
            engines.any { it.name == GOOGLE_TTS_PACKAGE } -> {
                Log.d(TAG, "Using Google TTS")
                GOOGLE_TTS_PACKAGE
            }
            engines.any { it.name == SAMSUNG_TTS_PACKAGE } -> {
                Log.d(TAG, "Using Samsung TTS")
                SAMSUNG_TTS_PACKAGE
            }
            engines.isNotEmpty() -> {
                Log.d(TAG, "Using default TTS: ${engines.first().name}")
                engines.first().name
            }
            else -> null
        }
    }
    
    private fun initTts() {
        textToSpeech = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                configureTts()
                
                // Pending utterances abspielen
                pendingUtterances.forEach { (text, utteranceId) ->
                    speakInternal(text, utteranceId)
                }
                pendingUtterances.clear()
                
                Log.d(TAG, "TTS initialized successfully with engine: $preferredEngine")
            } else {
                Log.e(TAG, "TTS initialization failed: $status")
            }
        }, preferredEngine)
    }
    
    private fun configureTts() {
        textToSpeech?.let { tts ->
            // Deutsche Sprache
            val result = tts.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA) {
                Log.w(TAG, "German language data missing, downloading...")
                // Versuche Sprachdaten zu installieren
                installGermanVoiceData()
            } else if (result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "German not supported, using default")
                tts.setLanguage(Locale.getDefault())
            }
            
            // Spreche etwas schneller für natürlichen Flow
            tts.setSpeechRate(1.1f)
            
            // Normale Tonhöhe
            tts.setPitch(1.0f)
            
            // Versuche eine weibliche Stimme zu finden (klingt oft natürlicher)
            setBestFemaleVoice()
            
            // Progress Listener
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Speaking started: $utteranceId")
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Speaking done: $utteranceId")
                    onSpeechComplete?.invoke()
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Speaking error: $utteranceId")
                    onSpeechComplete?.invoke()
                }
            })
        }
    }
    
    /**
     * Sucht die beste weibliche Stimme
     */
    private fun setBestFemaleVoice() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.let { tts ->
                val voices = tts.voices
                
                // Suche nach deutscher weiblicher Stimme
                val germanFemaleVoice = voices?.find { voice ->
                    voice.locale.language == "de" && 
                    (voice.name.contains("female", ignoreCase = true) ||
                     voice.name.contains("woman", ignoreCase = true) ||
                     voice.name.contains("de-de-x-sfg", ignoreCase = true) || // Google weibliche Stimme
                     voice.name.contains("de-de-x-nfh", ignoreCase = true))   // Google alternative
                }
                
                if (germanFemaleVoice != null) {
                    Log.d(TAG, "Selected voice: ${germanFemaleVoice.name}")
                    tts.voice = germanFemaleVoice
                } else {
                    // Fallback: Erste deutsche Stimme
                    val germanVoice = voices?.find { it.locale.language == "de" }
                    if (germanVoice != null) {
                        Log.d(TAG, "Using German voice: ${germanVoice.name}")
                        tts.voice = germanVoice
                    }
                }
            }
        }
    }
    
    /**
     * Öffnet Play Store oder Einstellungen um bessere Stimmen zu installieren
     */
    private fun installGermanVoiceData() {
        try {
            val installIntent = Intent()
            installIntent.action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
            installIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open TTS installer: ${e.message}")
        }
    }
    
    /**
     * Spricht den Text aus
     */
    fun speak(text: String, utteranceId: String = "kimi_response") {
        if (!isInitialized) {
            pendingUtterances.add(Pair(text, utteranceId))
            return
        }
        
        speakInternal(text, utteranceId)
    }
    
    private fun speakInternal(text: String, utteranceId: String) {
        textToSpeech?.let { tts ->
            // Stoppe aktuelle Sprache
            tts.stop()
            
            // Parameter für moderne Android Versionen
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                val hashMap = java.util.HashMap<String, String>()
                hashMap[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
                @Suppress("DEPRECATION")
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, hashMap)
            }
        }
    }
    
    /**
     * Stoppt aktuelle Sprachausgabe
     */
    fun stop() {
        textToSpeech?.stop()
    }
    
    /**
     * Prüft ob TTS verfügbar ist
     */
    fun isAvailable(): Boolean {
        return isInitialized
    }
    
    /**
     * Prüft ob Google TTS installiert ist (beste Qualität)
     */
    fun hasGoogleTts(): Boolean {
        return preferredEngine == GOOGLE_TTS_PACKAGE
    }
    
    /**
     * Gibt Info über aktuelle Stimme zurück
     */
    fun getCurrentVoiceInfo(): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            val voice = textToSpeech?.voice
            "Engine: ${preferredEngine ?: "default"}, Voice: ${voice?.name ?: "unknown"}, Locale: ${voice?.locale}"
        } else {
            "Engine: ${preferredEngine ?: "default"}"
        }
    }
    
    /**
     * Gibt Liste verfügbarer Stimmen zurück
     */
    fun getAvailableVoices(): List<Voice> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.voices?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    /**
     * Setzt eine spezifische Stimme
     */
    fun setVoice(voice: Voice) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.voice = voice
        }
    }
    
    /**
     * Setzt Sprachrate (0.5 = langsam, 1.0 = normal, 2.0 = schnell)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }
    
    /**
     * Öffnet TTS Einstellungen
     */
    fun openTtsSettings() {
        try {
            val intent = Intent()
            intent.action = "com.android.settings.TTS_SETTINGS"
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback zu allgemeinen Spracheinstellungen
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open settings: ${e2.message}")
            }
        }
    }
    
    /**
     * Cleanup
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}