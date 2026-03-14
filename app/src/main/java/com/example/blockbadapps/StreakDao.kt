package com.example.blockbadapps

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StreakDao {

    @Insert
    suspend fun insertStreak(record: StreakRecord)

    /** La racha más larga de todas las guardadas. */
    @Query("SELECT * FROM streak_records ORDER BY totalDays DESC LIMIT 1")
    suspend fun getBestStreak(): StreakRecord?

    /** Cuántas veces se ha reiniciado el contador. */
    @Query("SELECT COUNT(*) FROM streak_records")
    suspend fun getRelapseCount(): Int

    @Query("SELECT * FROM streak_records ORDER BY startDate DESC")
    suspend fun getAllStreaks(): List<StreakRecord>
}