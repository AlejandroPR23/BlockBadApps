package com.example.blockbadapps

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fuente única de verdad para racha y contadores de bloqueo.
 *
 * Historial de rachas  → Room  (datos valiosos a largo plazo)
 * Contadores diarios   → SharedPreferences  (lecturas rápidas y frecuentes)
 */
class StreakManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    private val db = AppDatabase.getInstance(context)

    companion object {
        const val PREF_STREAK_START      = "streak_start_ms"
        const val PREF_BLOCKED_TODAY     = "blocked_today_count"
        const val PREF_BLOCKED_TODAY_DT  = "blocked_today_date"   // "yyyy-MM-dd"
        const val PREF_BLOCKED_TOTAL     = "blocked_total_count"
    }

    // ── Racha ────────────────────────────────────────────────────────────────

    /** Días completos desde que comenzó la racha actual. Mínimo 0. */
    fun getCurrentStreakDays(): Int {
        val start = prefs.getLong(PREF_STREAK_START, 0L)
        if (start == 0L) return 0
        return maxOf(0, ((System.currentTimeMillis() - start) / (1000L * 60 * 60 * 24)).toInt())
    }

    fun getStreakStartDate(): Long = prefs.getLong(PREF_STREAK_START, 0L)

    /** Llama una sola vez al primer uso de la app. */
    fun initStreakIfNeeded() {
        if (prefs.getLong(PREF_STREAK_START, 0L) == 0L) {
            prefs.edit().putLong(PREF_STREAK_START, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Llama después de confirmar recaída.
     * Guarda la racha actual en Room y reinicia el contador.
     */
    suspend fun recordRelapse() {
        val days     = getCurrentStreakDays()
        val start    = getStreakStartDate()
        val attempts = getTotalBlocked()

        if (days > 0) {
            db.streakDao().insertStreak(
                StreakRecord(
                    startDate       = start,
                    endDate         = System.currentTimeMillis(),
                    totalDays       = days,
                    blockedAttempts = attempts
                )
            )
        }
        prefs.edit()
            .putLong(PREF_STREAK_START, System.currentTimeMillis())
            .putInt(PREF_BLOCKED_TOTAL, 0)
            .putInt(PREF_BLOCKED_TODAY, 0)
            .apply()
    }

    /** La racha más larga: compara BD + racha activa. */
    suspend fun getLongestStreak(): Int {
        val fromDb = db.streakDao().getBestStreak()?.totalDays ?: 0
        return maxOf(fromDb, getCurrentStreakDays())
    }

    suspend fun getRelapseCount(): Int = db.streakDao().getRelapseCount()

    // ── Contadores de bloqueo ────────────────────────────────────────────────

    fun logBlockedAttempt() {
        val today    = dateString()
        val savedDay = prefs.getString(PREF_BLOCKED_TODAY_DT, "")
        val todayCount = if (savedDay == today) prefs.getInt(PREF_BLOCKED_TODAY, 0) + 1 else 1

        prefs.edit()
            .putString(PREF_BLOCKED_TODAY_DT, today)
            .putInt(PREF_BLOCKED_TODAY, todayCount)
            .putInt(PREF_BLOCKED_TOTAL, getTotalBlocked() + 1)
            .apply()
    }

    fun getBlockedToday(): Int {
        val today = dateString()
        return if (prefs.getString(PREF_BLOCKED_TODAY_DT, "") == today)
            prefs.getInt(PREF_BLOCKED_TODAY, 0)
        else 0
    }

    fun getTotalBlocked(): Int = prefs.getInt(PREF_BLOCKED_TOTAL, 0)

    private fun dateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}