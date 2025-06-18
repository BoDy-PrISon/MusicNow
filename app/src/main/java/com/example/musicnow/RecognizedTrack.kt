package com.example.musicnow

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognized_tracks")
data class RecognizedTrack(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val artist: String?,
    val album: String?,
    val coverUrl: String?,
    val releaseDate: String?,
    val genre: String?,
    val confidence: Float?,
    val bpm: Float?,
    val duration: Int?,
    val spotifyUrl: String?,
    val appleMusicUrl: String?,
    val songLink: String?,
    val yandexMusicUrl: String?,
    val youtubeMusicUrl: String?,
    val mood: String?,
    val instruments: List<String>? = null
) 