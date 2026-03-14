package com.example.blockbadapps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class BlockedScreenActivity : AppCompatActivity() {

    private var countdownTimer: CountDownTimer? = null

    companion object {
        const val VIDEO_URL = "https://www.youtube.com/watch?v=zBxBT3k7Jb0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mostrarse encima de la pantalla de bloqueo y encender la pantalla
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON  or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        supportActionBar?.hide()
        setContentView(R.layout.activity_blocked_screen)

        val streakManager = StreakManager(this)
        val days          = streakManager.getCurrentStreakDays()

        // ── Mensaje motivacional aleatorio ───────────────────────────────────
        findViewById<TextView>(R.id.motivationalMessageTextView).text =
            BadgeSystem.randomMessage()

        // ── Recordatorio de racha ────────────────────────────────────────────
        findViewById<TextView>(R.id.streakReminderTextView).text = when (days) {
            0    -> "Hoy es tu día 1. Empieza bien."
            1    -> "Llevas 1 día. No lo pierdas."
            else -> "Llevas $days días. No los pierdas."
        }

        // ── Cuenta regresiva → abre video automáticamente ───────────────────
        val countdownTv    = findViewById<TextView>(R.id.countdownTextView)
        val watchNowButton = findViewById<MaterialButton>(R.id.watchNowButton)

        countdownTimer = object : CountDownTimer(5_000, 1_000) {
            override fun onTick(millisLeft: Long) {
                val secs = (millisLeft / 1000) + 1
                countdownTv.text = "Video motivacional en ${secs}s..."
            }
            override fun onFinish() {
                countdownTv.text = ""
                openVideo()
            }
        }.start()

        watchNowButton.setOnClickListener {
            countdownTimer?.cancel()
            openVideo()
        }
        findViewById<MaterialButton>(R.id.goHomeButton).setOnClickListener {
            countdownTimer?.cancel()
            goHome()
        }
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }

    // El usuario no puede presionar atrás para volver al contenido bloqueado
    @Deprecated("Required override")
    override fun onBackPressed() { goHome() }

    private fun openVideo() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VIDEO_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (_: Exception) { /* ignore */ }
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}