package com.example.musicnow

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert
    suspend fun insert(track: RecognizedTrack)

    @Query("SELECT * FROM recognized_tracks ORDER BY id DESC")
    fun getAllTracks(): Flow<List<RecognizedTrack>>

    @Delete
    suspend fun delete(track: RecognizedTrack)
} 