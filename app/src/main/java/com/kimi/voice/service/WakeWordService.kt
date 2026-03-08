package com.kimi.voice.service

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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

class WakeWordService : Service() {
    
    companion object {
        const val TAG = "KimiWakeWord"
        const val CHANNEL_ID = "kimi_voice_channel"
        const val NOTIFICATION_ID = 1
        const val WAKEWORD_KEY = "kimi"
    }
    
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var openClawApi: OpenClawApi
    private lateinit var intentHandler: IntentHandler
    private lateinit var ttsManager: HybridTtsManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var porcupineBuffer: ShortArray? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        openClawApi = OpenClawApi(this)
        intentHandler = IntentHandler(this)
        ttsManager = HybridTtsManager(this)
        
        initSpeechRecognizer()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        startWakeWordDetection()
        
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE") // Deutsch
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }
    
    private fun startWakeWordDetection() {
        try {
            // Porcupine mit "Hey Kimi" Wake Word initialisieren
            porcupine = Porcupine.Builder()
                .setAccessKey("E1Cmpj9Q7OJa28ZttCpW0ZhhOdR4V5jAN28ec/X+MQoiMjtbVKzFcg==")
                .setKeywordPaths(arrayOf("hey-kimi.ppn")) // Custom Wake Word
                .setSensitivities(floatArrayOf(0.7f))
                .build(applicationContext)
            
            val sampleRate = porcupine?.sampleRate ?: 16000
            val frameLength = porcupine?.frameLength ?: 512
            
            porcupineBuffer = ShortArray(frameLength)
            
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(frameLength * 2)
            )
            
            isListening = true
            audioRecord?.startRecording()
            
            // Background Thread für Audio-Verarbeitung
            Thread {
                processAudio(frameLength)
            }.start()
            
            Log.d(TAG, "Wake word detection started")
            
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine error: ${e.message}")
            // Fallback: Button-basierte Aktivierung
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
        }
    }
    
    private fun processAudio(frameLength: Int) {
        val buffer = porcupineBuffer ?: return
        
        while (isListening && !Thread.interrupted()) {
            try {
                val r = audioRecord?.read(buffer, 0, frameLength) ?: -1
                
                if (r == frameLength) {
                    val keywordIndex = porcupine?.process(buffer) ?: -1
                    
                    if (keywordIndex >= 0) {
                        Log.d(TAG, "Wake word detected!")
                        handler.post { onWakeWordDetected() }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio processing error: ${e.message}")
            }
        }
    }
    
    private fun onWakeWordDetected() {
        // Vibrations-Feedback
        vibrate()
        
        // Bestätigung sprechen
        ttsManager.speak("Ja?", "wake_ack")
        
        // Visuelles Feedback
        showListeningOverlay()
        
        // Spracherkennung starten
        startSpeechRecognition()
    }
    
    private fun startSpeechRecognition() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
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
                hideListeningOverlay()
                restartWakeWordDetection()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()
                
                Log.d(TAG, "Command: $command")
                
                if (command != null) {
                    processCommand(command)
                }
                
                hideListeningOverlay()
                restartWakeWordDetection()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer.startListening(recognizerIntent)
    }
    
    private fun processCommand(command: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Feedback: "Einen Moment..."
                ttsManager.speak("Einen Moment...", "thinking")
                
                // Sende an OpenClaw
                val result = openClawApi.sendCommand(command)
                
                result.onSuccess { response ->
                    Log.d(TAG, "Response: ${response.text}")
                    
                    // Sprich die Antwort
                    ttsManager.speak(response.text, "response")
                    
                    // Zeige Antwort als Overlay
                    showResponseOverlay(response.text)
                    
                    // Führe Aktionen aus
                    intentHandler.handleCommand(command, response)
                    
                }.onFailure { error ->
                    Log.e(TAG, "API error: ${error.message}")
                    val errorText = "Entschuldigung, ich konnte dich nicht erreichen."
                    ttsManager.speak(errorText, "error")
                    showResponseOverlay(errorText)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        }
    }
    
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        
        vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    }
    
    private fun showListeningOverlay() {
        // TODO: Floating View implementieren
        Log.d(TAG, "Showing listening overlay")
    }
    
    private fun hideListeningOverlay() {
        Log.d(TAG, "Hiding listening overlay")
    }
    
    private fun showResponseOverlay(text: String) {
        // TODO: Floating View mit Antwort
        Log.d(TAG, "Response: $text")
    }
    
    private fun restartWakeWordDetection() {
        // Nach kurzer Verzögerung Wake Word wieder aktivieren
        handler.postDelayed({
            // Wake Word Detection läuft weiter, nichts zu tun
        }, 1000)
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
        wakeLock.acquire(10*60*1000L) // 10 Minuten
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        isListening = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            porcupine?.delete()
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