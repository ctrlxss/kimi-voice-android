package com.kimi.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kimi.voice.api.OpenClawApi
import com.kimi.voice.service.WakeWordService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        const val PERMISSION_REQUEST_CODE = 100
        const val OVERLAY_REQUEST_CODE = 101
        const val BATTERY_OPT_REQUEST_CODE = 102
    }
    
    private lateinit var statusText: TextView
    private lateinit var gatewayInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var startButton: Button
    private lateinit var testButton: Button
    
    private lateinit var openClawApi: OpenClawApi
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        openClawApi = OpenClawApi(this)
        
        initViews()
        checkPermissions()
        loadSettings()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        gatewayInput = findViewById(R.id.gatewayInput)
        tokenInput = findViewById(R.id.tokenInput)
        startButton = findViewById(R.id.startButton)
        testButton = findViewById(R.id.testButton)
        
        startButton.setOnClickListener {
            if (isServiceRunning()) {
                stopWakeWordService()
            } else {
                saveSettings()
                startWakeWordService()
            }
        }
        
        testButton.setOnClickListener {
            testConnection()
        }
        
        updateUI()
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
        
        // Overlay Permission für Floating UI
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        }
        
        // Battery Optimization ignorieren
        checkBatteryOptimization()
    }
    
    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Overlay-Berechtigung")
            .setMessage("Kimi Voice benötigt die Berechtigung, über anderen Apps angezeigt zu werden (für die Antwort-Anzeige).")
            .setPositiveButton("Erlauben") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivityForResult(intent, OVERLAY_REQUEST_CODE)
            }
            .setNegativeButton("Später", null)
            .show()
    }
    
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle("Akku-Optimierung")
                .setMessage("Für zuverlässiges Lauschen im Hintergrund, bitte Akku-Optimierung deaktivieren.")
                .setPositiveButton("Einstellungen") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, BATTERY_OPT_REQUEST_CODE)
                }
                .setNegativeButton("Später", null)
                .show()
        }
    }
    
    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "Kimi Voice gestartet", Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    private fun stopWakeWordService() {
        stopService(Intent(this, WakeWordService::class.java))
        Toast.makeText(this, "Kimi Voice gestoppt", Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    private fun isServiceRunning(): Boolean {
        // Vereinfachte Prüfung - in Produktion besser über SharedPreferences oder Binder
        return false
    }
    
    private fun testConnection() {
        val gateway = gatewayInput.text.toString()
        val token = tokenInput.text.toString()
        
        if (gateway.isEmpty()) {
            Toast.makeText(this, "Bitte Gateway URL eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        
        saveSettings()
        
        statusText.text = "Teste Verbindung..."
        
        CoroutineScope(Dispatchers.Main).launch {
            val result = openClawApi.sendCommand("Hallo, das ist ein Test")
            
            result.onSuccess { response ->
                statusText.text = "✅ Verbunden!\nAntwort: ${response.text}"
            }.onFailure { error ->
                statusText.text = "❌ Fehler: ${error.message}"
            }
        }
    }
    
    private fun loadSettings() {
        val prefs = getSharedPreferences("kimi_prefs", MODE_PRIVATE)
        gatewayInput.setText(prefs.getString("gateway_url", "http://192.168.1.100:8080"))
        tokenInput.setText(prefs.getString("gateway_token", ""))
    }
    
    private fun saveSettings() {
        getSharedPreferences("kimi_prefs", MODE_PRIVATE).edit().apply {
            putString("gateway_url", gatewayInput.text.toString())
            putString("gateway_token", tokenInput.text.toString())
            apply()
        }
    }
    
    private fun updateUI() {
        // In Produktion: Prüfe ob Service läuft
        startButton.text = "Wake Word Service starten"
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Berechtigungen erteilt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Berechtigungen benötigt", Toast.LENGTH_LONG).show()
            }
        }
    }
}