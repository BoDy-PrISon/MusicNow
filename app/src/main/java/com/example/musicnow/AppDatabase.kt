package com.example.musicnow

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [RecognizedTrack::class], version = 4)
@TypeConverters(ListStringTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
} 