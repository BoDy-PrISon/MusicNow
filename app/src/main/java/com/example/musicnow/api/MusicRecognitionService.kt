package com.example.musicnow.api

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface MusicRecognitionService {
    @Multipart
    @POST("recognize")
    suspend fun recognizeMusic(@Part file: MultipartBody.Part): RecognitionResponse
}