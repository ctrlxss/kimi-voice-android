package com.kimi.voice.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.kimi.voice.api.OpenClawApi
import java.net.URLEncoder

class IntentHandler(private val context: Context) {
    
    companion object {
        const val TAG = "IntentHandler"
    }
    
    fun handleCommand(command: String, response: OpenClawApi.CommandResponse) {
        val lowerCommand = command.lowercase()
        
        when {
            // Navigation
            lowerCommand.contains("navigier") || 
            lowerCommand.contains("weg") ||
            lowerCommand.contains("route") -> {
                extractDestination(command)?.let { destination ->
                    openGoogleMapsNavigation(destination)
                }
            }
            
            // Musik
            lowerCommand.contains("spotify") ||
            lowerCommand.contains("musik") ||
            lowerCommand.contains("lied") ||
            lowerCommand.contains("song") -> {
                val query = command.replace(Regex("(spiel|starte|öffne|spotify|musik|lied|song)"), "", RegexOption.IGNORE_CASE).trim()
                openSpotify(query)
            }
            
            // Anrufe
            lowerCommand.contains("ruf") ||
            lowerCommand.contains("anruf") ||
            lowerCommand.contains("telefon") -> {
                extractContact(command)?.let { contact ->
                    makePhoneCall(contact)
                }
            }
            
            // Nachrichten
            lowerCommand.contains("schreib") ||
            lowerCommand.contains("nachricht") ||
            lowerCommand.contains("whatsapp") -> {
                openWhatsApp()
            }
            
            // Wecker
            lowerCommand.contains("wecker") ||
            lowerCommand.contains("weck mich") -> {
                extractTime(command)?.let { time ->
                    setAlarm(time)
                }
            }
            
            // Timer
            lowerCommand.contains("timer") -> {
                extractDuration(command)?.let { minutes ->
                    setTimer(minutes)
                }
            }
            
            // Kalender
            lowerCommand.contains("termin") ||
            lowerCommand.contains("kalender") -> {
                openCalendar()
            }
            
            // Kamera
            lowerCommand.contains("foto") ||
            lowerCommand.contains("kamera") ||
            lowerCommand.contains("bild") -> {
                openCamera()
            }
            
            // Taschenlampe
            lowerCommand.contains("taschenlampe") ||
            lowerCommand.contains("licht") -> {
                toggleFlashlight()
            }
            
            // Einstellungen
            lowerCommand.contains("einstellungen") ||
            lowerCommand.contains("wlan") ||
            lowerCommand.contains("bluetooth") -> {
                openSettings()
            }
            
            // YouTube
            lowerCommand.contains("youtube") ||
            lowerCommand.contains("video") -> {
                val query = command.replace(Regex("(öffne|youtube|video|suche|nach)"), "", RegexOption.IGNORE_CASE).trim()
                openYouTube(query)
            }
        }
    }
    
    private fun openGoogleMapsNavigation(destination: String) {
        try {
            val encodedDestination = URLEncoder.encode(destination, "UTF-8")
            val uri = Uri.parse("google.navigation:q=$encodedDestination")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opening Maps navigation to: $destination")
        } catch (e: ActivityNotFoundException) {
            // Fallback zu Web
            val encodedDestination = URLEncoder.encode(destination, "UTF-8")
            val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDestination")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
    
    private fun openSpotify(query: String?) {
        try {
            val uri = if (query.isNullOrEmpty()) {
                Uri.parse("spotify:")
            } else {
                val encoded = URLEncoder.encode(query, "UTF-8")
                Uri.parse("spotify:search:$encoded")
            }
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Play Store öffnen
            openPlayStore("com.spotify.music")
        }
    }
    
    private fun makePhoneCall(number: String) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    private fun openWhatsApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp:")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openPlayStore("com.whatsapp")
        }
    }
    
    private fun setAlarm(time: String) {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Kimi Alarm")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Alarm app not found")
        }
    }
    
    private fun setTimer(minutes: Int) {
        val seconds = minutes * 60
        
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Kimi Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Timer app not found")
        }
    }
    
    private fun openCalendar() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = CalendarContract.CONTENT_URI.buildUpon()
                .appendPath("time")
                .build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback zu Kamera-App öffnen
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.camera", "com.android.camera.Camera")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Camera not available")
            }
        }
    }
    
    private fun toggleFlashlight() {
        // Benötigt Kamera-Permission und API-Zugriff
        // Für jetzt: Einstellungen öffnen
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open settings")
        }
    }
    
    private fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    
    private fun openYouTube(query: String?) {
        try {
            val uri = if (query.isNullOrEmpty()) {
                Uri.parse("vnd.youtube:")
            } else {
                val encoded = URLEncoder.encode(query, "UTF-8")
                Uri.parse("vnd.youtube:search?q=$encoded")
            }
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Web-Version
            val encoded = URLEncoder.encode(query ?: "", "UTF-8")
            val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
    
    private fun openPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
    
    // Hilfsfunktionen zum Extrahieren von Informationen
    
    private fun extractDestination(command: String): String? {
        // "Navigier nach Stuttgart" -> "Stuttgart"
        val patterns = listOf(
            "nach\\s+(.+?)(?:\\s+bitte)?$",
            "zu\\s+(.+?)(?:\\s+bitte)?$",
            "nach\\s+(.+?)(?:\\s+und)?"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(command)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    private fun extractContact(command: String): String? {
        // "Ruf Mama an" -> "Mama"
        val patterns = listOf(
            "ruf\\s+(.+?)\\s+an",
            "anruf\\s+(.+?)(?:\\s+bitte)?",
            "telefonier\\s+mit\\s+(.+)"
        )
        
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(command)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        
        return null
    }
    
    private fun extractTime(command: String): String? {
        // "Wecker auf 7 Uhr" -> "07:00"
        val hourPattern = Regex("(\\d+)(?::(\\d+))?\\s*Uhr", RegexOption.IGNORE_CASE)
        val match = hourPattern.find(command)
        
        if (match != null) {
            val hour = match.groupValues[1].padStart(2, '0')
            val minute = match.groupValues[2].ifEmpty { "00" }.padStart(2, '0')
            return "$hour:$minute"
        }
        
        return null
    }
    
    private fun extractDuration(command: String): Int? {
        // "Timer für 5 Minuten" -> 5
        val minutePattern = Regex("(\\d+)\\s*Minuten?", RegexOption.IGNORE_CASE)
        val match = minutePattern.find(command)
        
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}