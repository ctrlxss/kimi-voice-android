package com.kimi.voice.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.kimi.voice.R

/**
 * Floating Overlay für Kimi Antworten
 */
class FloatingResponseWindow(private val context: Context) {
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    
    fun show(text: String, durationMs: Long = 5000) {
        handler.post {
            try {
                remove() // Entferne altes Overlay
                
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 200 // Abstand von oben
                }
                
                val inflater = LayoutInflater.from(context)
                floatingView = inflater.inflate(R.layout.floating_response, null)
                
                val textView = floatingView?.findViewById<TextView>(R.id.responseText)
                textView?.text = text
                
                // Animation
                val animation = AnimationUtils.loadAnimation(context, R.anim.fade_in_slide_down)
                floatingView?.startAnimation(animation)
                
                windowManager?.addView(floatingView, params)
                
                // Auto-remove
                handler.postDelayed({ remove() }, durationMs)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun showListening() {
        show("🎤 Ich höre...", 10000)
    }
    
    fun remove() {
        handler.post {
            try {
                floatingView?.let {
                    val animation = AnimationUtils.loadAnimation(context, R.anim.fade_out)
                    it.startAnimation(animation)
                    
                    handler.postDelayed({
                        if (it.isAttachedToWindow) {
                            windowManager?.removeView(it)
                        }
                    }, 300)
                }
                floatingView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}