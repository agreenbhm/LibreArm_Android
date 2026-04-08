package com.ptylr.librearm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert
    suspend fun insert(reading: ReadingEntity): Long

    @Query("SELECT * FROM readings ORDER BY timestamp DESC")
    fun getAllReadings(): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE timestamp BETWEEN :startMillis AND :endMillis ORDER BY timestamp DESC")
    fun getReadingsInRange(startMillis: Long, endMillis: Long): Flow<List<ReadingEntity>>

    @Delete
    suspend fun delete(reading: ReadingEntity)

    @Query("SELECT COUNT(*) FROM readings")
    suspend fun getCount(): Int
}
