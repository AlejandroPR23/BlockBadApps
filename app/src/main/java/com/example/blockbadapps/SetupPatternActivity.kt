package com.example.blockbadapps

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupPatternActivity : AppCompatActivity() {

    private lateinit var patternLockView: PatternLockView
    private lateinit var instructionTextView: TextView
    private lateinit var cancelButton: Button
    private lateinit var patternManager: PatternManager

    private var firstPattern: String? = null
    private var isConfirming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_pattern)

        patternManager = PatternManager(this)

        patternLockView = findViewById(R.id.patternLockView)
        instructionTextView = findViewById(R.id.instructionTextView)
        cancelButton = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener {
            finish()
        }

        patternLockView.onPatternListener = { pattern ->
            handlePattern(pattern)
        }
    }

    private fun handlePattern(pattern: String) {
        if (pattern.length < 4) {
            Toast.makeText(this, "Pattern too short. Connect at least 4 dots", Toast.LENGTH_SHORT).show()
            patternLockView.showError()
            return
        }

        if (!isConfirming) {
            // Primer patrón
            firstPattern = pattern
            isConfirming = true
            instructionTextView.text = "Draw the pattern again to confirm"
            patternLockView.showSuccess()
        } else {
            // Confirmar patrón
            if (pattern == firstPattern) {
                patternManager.savePattern(pattern)
                Toast.makeText(this, "Pattern saved successfully!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, "Patterns don't match. Try again", Toast.LENGTH_SHORT).show()
                patternLockView.showError()
                firstPattern = null
                isConfirming = false
                instructionTextView.text = "Draw your unlock pattern"
            }
        }
    }
}