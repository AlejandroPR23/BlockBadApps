package com.example.blockbadapps

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ProgressActivity : AppCompatActivity() {

    private lateinit var streakManager: StreakManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        supportActionBar?.title = "Mi progreso"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        streakManager = StreakManager(this)
        loadData()
        setupRelapseButton()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Carga de datos ───────────────────────────────────────────────────────

    private fun loadData() {
        val days = streakManager.getCurrentStreakDays()
        updateStreakCard(days)
        updateStats(days)
        buildBadgeList(days)
    }

    private fun updateStreakCard(days: Int) {
        val earned = BadgeSystem.getEarned(days).lastOrNull()
        findViewById<TextView>(R.id.streakEmojiTextView).text = earned?.emoji ?: "🌱"
        findViewById<TextView>(R.id.streakDaysTextView).text  = days.toString()

        val next = BadgeSystem.getNextBadge(days)
        val pb   = findViewById<ProgressBar>(R.id.streakProgressBar)

        if (next != null) {
            val daysLeft = next.requiredDays - days
            val progress = (BadgeSystem.progressToNext(days) * 100).toInt()
            findViewById<TextView>(R.id.nextBadgeTextView).text =
                "Próxima medalla: ${next.emoji} ${next.name} — faltan $daysLeft días"
            pb.progress = progress
        } else {
            findViewById<TextView>(R.id.nextBadgeTextView).text = "¡Todas las medallas obtenidas! 🏆"
            pb.progress = 100
        }
    }

    private fun updateStats(days: Int) {
        findViewById<TextView>(R.id.blockedTodayTextView).text =
            streakManager.getBlockedToday().toString()

        // La mejor racha requiere consulta a Room → coroutine
        lifecycleScope.launch {
            val longest = streakManager.getLongestStreak()
            runOnUiThread {
                findViewById<TextView>(R.id.longestStreakTextView).text = longest.toString()
            }
        }
    }

    private fun buildBadgeList(days: Int) {
        val container = findViewById<LinearLayout>(R.id.badgesContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (badge in BadgeSystem.ALL) {
            val earned   = badge.requiredDays <= days
            val itemView = inflater.inflate(R.layout.list_item_badge, container, false)

            itemView.findViewById<TextView>(R.id.badgeEmojiTextView).text =
                if (earned) badge.emoji else "🔒"
            itemView.findViewById<TextView>(R.id.badgeNameTextView).text  = badge.name
            itemView.findViewById<TextView>(R.id.badgeDescTextView).text  = badge.description
            itemView.findViewById<TextView>(R.id.badgeDaysTextView).text  =
                if (earned) "✓ Obtenida" else "${badge.requiredDays} días"

            if (!earned) itemView.alpha = 0.4f

            container.addView(itemView)
        }
    }

    // ── Botón de recaída ─────────────────────────────────────────────────────

    private fun setupRelapseButton() {
        findViewById<MaterialButton>(R.id.relapseButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Registrar recaída")
                .setMessage(
                    "Esto va a reiniciar tu contador a 0.\n\n" +
                            "Tu racha anterior quedará guardada en el historial.\n\n" +
                            "Recuerda: registrar la recaída honestamente es parte de la recuperación."
                )
                .setPositiveButton("Sí, reiniciar") { _, _ ->
                    lifecycleScope.launch {
                        streakManager.recordRelapse()
                        runOnUiThread {
                            Toast.makeText(
                                this@ProgressActivity,
                                "Contador reiniciado. Cada intento cuenta. Ánimo.",
                                Toast.LENGTH_LONG
                            ).show()
                            loadData()   // Refresca toda la pantalla
                        }
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}