package com.example.blockbadapps

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streak_records")
data class StreakRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startDate: Long,        // timestamp inicio de racha
    val endDate: Long,          // timestamp cuando terminó (recaída)
    val totalDays: Int,         // cuántos días duró
    val blockedAttempts: Int    // cuántos bloqueos hubo en esa racha
)