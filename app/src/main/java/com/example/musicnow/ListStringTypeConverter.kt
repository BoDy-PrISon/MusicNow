package com.example.musicnow

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ListStringTypeConverter {

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) {
            return null
        }
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(string: String?): List<String>? {
        if (string == null) {
            return null
        }
        val type = object : TypeToken<List<String>>() {}.type
        val gson = Gson()
        return gson.fromJson(string, type)
    }
} 