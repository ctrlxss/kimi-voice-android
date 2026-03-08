package com.kimi.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kimi.voice.MainActivity
import com.kimi.voice.R
import com.kimi.voice.api.OpenClawApi
import com.kimi.voice.util.IntentHandler
import com.kimi.voice.util.HybridTtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Einfacherer Service ohne Porcupine - nutzt Android SpeechRecognizer
 * mit TTS Feedback für alle Antworten
 */
class SimpleVoiceService : Service() {
    
    companion object {
        const val TAG = "SimpleVoiceService"
        const val CHANNEL_ID = "kimi_voice_channel"
        const val NOTIFICATION_ID = 1
        
        // Keywords die wir erkennen wollen
        val WAKE_WORDS = listOf("kimi", "hey kimi", "ey kimi", "hey kim")
    }
    
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var openClawApi: OpenClawApi
    private lateinit var intentHandler: IntentHandler
    private lateinit var ttsManager: HybridTtsManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var consecutiveListens = 0
    private val maxConsecutiveListens = 100
    private var isProcessing = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SimpleVoiceService created")
        
        openClawApi = OpenClawApi(this)
        intentHandler = IntentHandler(this)
        ttsManager = HybridTtsManager(this)
        
        initSpeechRecognizer()
        createNotificationChannel()
        acquireWakeLock()
        
        // TTS Callback
        ttsManager.onSpeechComplete = {
            // Wenn TTS fertig ist, weiter hören
            if (isListening) {
                restartListeningDelayed()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        startListeningLoop()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition not available")
            return
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }
    
    private fun startListeningLoop() {
        if (isProcessing) return
        
        if (consecutiveListens >= maxConsecutiveListens) {
            Log.d(TAG, "Max consecutive listens reached, restarting service")
            restartService()
            return
        }
        
        consecutiveListens++
        isListening = true
        
        try {
            setupRecognitionListener()
            speechRecognizer.startListening(recognizerIntent)
            Log.d(TAG, "Started listening (#$consecutiveListens)")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recognition: ${e.message}")
            restartListeningDelayed()
        }
    }
    
    private fun setupRecognitionListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                Log.d(TAG, "Ready for speech")
            }
            
            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech began")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }
            
            override fun onError(error: Int) {
                Log.e(TAG, "Speech recognition error: $error")
                if (!isProcessing) {
                    restartListeningDelayed()
                }
            }
            
            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d(TAG, "Results: $matches")
                
                if (!matches.isNullOrEmpty()) {
                    val text = matches.first().lowercase()
                    
                    // Prüfe auf Wake Word
                    if (containsWakeWord(text)) {
                        Log.d(TAG, "Wake word detected in: $text")
                        isProcessing = true
                        vibrate()
                        
                        // Bestätigungs-Sound
                        ttsManager.speak("Ja?", "wake_ack")
                        
                        // Extrahiere Befehl
                        val command = extractCommand(text)
                        if (command.isNotEmpty()) {
                            processCommand(command)
                        } else {
                            // Nur "Hey Kimi" - warte auf Befehl
                            startExtendedListening()
                        }
                    } else {
                        Log.d(TAG, "No wake word detected: $text")
                        restartListeningDelayed()
                    }
                } else {
                    restartListeningDelayed()
                }
            }
            
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches.first().lowercase()
                    if (containsWakeWord(text)) {
                        Log.d(TAG, "Wake word in partial result: $text")
                    }
                }
            }
            
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
    }
    
    private fun containsWakeWord(text: String): Boolean {
        return WAKE_WORDS.any { text.contains(it) }
    }
    
    private fun extractCommand(text: String): String {
        for (wakeWord in WAKE_WORDS) {
            val index = text.indexOf(wakeWord)
            if (index >= 0) {
                val after = text.substring(index + wakeWord.length).trim()
                return after.replace(Regex("^(bitte|und|dann|jetzt|,)\\s*"), "", RegexOption.IGNORE_CASE)
            }
        }
        return ""
    }
    
    private fun startExtendedListening() {
        val extendedIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ich höre...")
        }
        
        handler.postDelayed({
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    Log.e(TAG, "Extended listening error: $error")
                    isProcessing = false
                    restartListeningDelayed()
                }
                
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val command = matches.first()
                        Log.d(TAG, "Extended command: $command")
                        processCommand(command)
                    } else {
                        isProcessing = false
                        restartListeningDelayed()
                    }
                }
                
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            
            speechRecognizer.startListening(extendedIntent)
        }, 800)
    }
    
    private fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Feedback: "Einen Moment..."
                ttsManager.speak("Einen Moment...", "thinking")
                
                val result = openClawApi.sendCommand(command)
                
                result.onSuccess { response ->
                    Log.d(TAG, "Response: ${response.text}")
                    
                    // Sprich die Antwort
                    ttsManager.speak(response.text, "response")
                    
                    // Zeige auch visuelles Feedback
                    showResponseOverlay(response.text)
                    
                    // Führe Aktion aus
                    intentHandler.handleCommand(command, response)
                    
                    isProcessing = false
                    
                }.onFailure { error ->
                    Log.e(TAG, "API error: ${error.message}")
                    val errorText = "Tut mir leid, ich habe dich nicht verstanden."
                    ttsManager.speak(errorText, "error")
                    showResponseOverlay(errorText)
                    isProcessing = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                isProcessing = false
            }
        }
    }
    
    private fun restartListeningDelayed() {
        handler.postDelayed({
            if (isListening && !isProcessing) {
                startListeningLoop()
            }
        }, 1000)
    }
    
    private fun restartService() {
        val intent = Intent(this, SimpleVoiceService::class.java)
        stopService(intent)
        startService(intent)
    }
    
    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration error: ${e.message}")
        }
    }
    
    private fun showResponseOverlay(text: String) {
        Log.d(TAG, "Response: $text")
        handler.post {
            android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kimi Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background voice recognition"
                setSound(null, null)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kimi Voice")
            .setContentText("Sage \"Hey Kimi\" um zu starten")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KimiVoice::WakeLock"
        )
        wakeLock.acquire(10*60*1000L)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        isListening = false
        
        try {
            speechRecognizer.destroy()
            ttsManager.shutdown()
            if (::wakeLock.isInitialized && wakeLock.isHeld) {
                wakeLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }
}