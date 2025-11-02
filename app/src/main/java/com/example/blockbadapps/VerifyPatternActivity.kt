package com.example.blockbadapps

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VerifyPatternActivity : AppCompatActivity() {

    private lateinit var patternLockView: PatternLockView
    private lateinit var patternManager: PatternManager
    private var attemptCount = 0
    private val maxAttempts = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_pattern)

        patternManager = PatternManager(this)

        patternLockView = findViewById(R.id.patternLockView)
        val instructionTextView = findViewById<TextView>(R.id.instructionTextView)

        patternLockView.onPatternListener = { pattern ->
            if (patternManager.verifyPattern(pattern)) {
                patternLockView.showSuccess()
                Toast.makeText(this, "Pattern correct!", Toast.LENGTH_SHORT).show()

                // Esperar un momento antes de cerrar para que se vea la animación
                patternLockView.postDelayed({
                    setResult(RESULT_OK)
                    finish()
                }, 300)
            } else {
                attemptCount++
                val remaining = maxAttempts - attemptCount

                if (remaining > 0) {
                    Toast.makeText(this, "Incorrect pattern. $remaining attempts remaining", Toast.LENGTH_SHORT).show()
                    patternLockView.showError()
                } else {
                    Toast.makeText(this, "Too many attempts. App locked", Toast.LENGTH_LONG).show()
                    setResult(RESULT_CANCELED)
                    finishAffinity() // Cierra la app completamente
                }
            }
        }
    }

    override fun onBackPressed() {
        // Evitar que el usuario salga sin ingresar el patrón
        // En lugar de cerrar, minimizar la app
        moveTaskToBack(true)
    }
}