package com.example.musicnow


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicnow.api.RecognitionResponse
import com.example.musicnow.api.MusicRecognitionService
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.room.Room
import com.example.musicnow.AppDatabase
import com.example.musicnow.RecognizedTrack
import com.example.musicnow.TrackDao
import com.example.musicnow.MusicRecognizerApp
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import android.content.Context
import com.example.musicnow.SettingsManager

enum class RecognitionState {
    IDLE, RECORDING, RECOGNIZING, SUCCESS, ERROR
}

class MusicViewModel : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recognitionState = MutableStateFlow(RecognitionState.IDLE)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState

    private val _recognitionResult = MutableStateFlow<RecognitionResponse?>(null)
    val recognitionResult: StateFlow<RecognitionResponse?> = _recognitionResult

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var player: ExoPlayer? = null
    private var previewUrl: String? = null

    private var _baseUrl: String? = null
    private var _apiService: MusicRecognitionService? = null

    private val db by lazy {
        Room.databaseBuilder(
            MusicRecognizerApp.appContext,
            AppDatabase::class.java,
            "musicnow-db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    private val trackDao: TrackDao by lazy { db.trackDao() }

    private val _libraryTracks = MutableStateFlow<List<RecognizedTrack>>(emptyList())
    val libraryTracks: StateFlow<List<RecognizedTrack>> = _libraryTracks.asStateFlow()

    init {
        loadLibraryTracks()
    }

    fun setRecordingState(isRecording: Boolean) {
        _isRecording.value = isRecording
        if (isRecording) {
            _recognitionState.value = RecognitionState.RECORDING
        }
    }

    fun updateBaseUrl(context: Context, newUrl: String) {
        SettingsManager.setBaseUrl(context, newUrl)
        _baseUrl = newUrl
        _apiService = null // сбрасываем, чтобы пересоздать при следующем обращении
    }

    private fun getApiService(context: Context): MusicRecognitionService {
        val url = _baseUrl ?: SettingsManager.getBaseUrl(context)
        if (_apiService == null || url != _baseUrl) {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            _apiService = retrofit.create(MusicRecognitionService::class.java)
            _baseUrl = url
        }
        return _apiService!!
    }

    fun recognizeMusic(audioFilePath: String, context: Context) {
        viewModelScope.launch {
            try {
                _recognitionState.value = RecognitionState.RECOGNIZING

                val response = withContext(Dispatchers.IO) {
                    val file = File(audioFilePath)
                    Log.d("MusicViewModel", "Отправляем файл: ${file.absolutePath}, размер: ${file.length()} байт")
                    val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    getApiService(context).recognizeMusic(body)
                }

                Log.d("MusicViewModel", "Ответ сервера: $response")
                Log.d("MusicViewModel", "Метаданные: ${response.metadata}")
                Log.d("MusicViewModel", "AudD данные: ${response.audd}")
                _recognitionResult.value = response
                _recognitionState.value = RecognitionState.SUCCESS

                // Сохраняем URL для превью
                previewUrl = response.audd?.spotify?.preview_url
                    ?: response.audd?.apple_music?.previews?.firstOrNull()?.url

                // Перемещаем создание и сохранение трека вне блока if (response.audd != null)
                if (response != null) { // Проверяем, что ответ сервера получен
                    val audd = response.audd
                    val title = audd?.title
                    val artist = audd?.artist
                    // Проверяем, что есть хотя бы название или исполнитель
                    if (!title.isNullOrBlank() || !artist.isNullOrBlank()) { // Изменяем условие на OR
                        val track = RecognizedTrack(
                            title = title,
                            artist = artist,
                            album = audd?.album,
                            coverUrl = audd?.spotify?.album?.images?.firstOrNull()?.url
                                ?: audd?.apple_music?.artwork?.url,
                            releaseDate = audd?.release_date,
                            genre = audd?.spotify?.album?.genreNames?.firstOrNull() ?: response.metadata?.genre,
                            confidence = response.metadata?.confidence?.toFloat(),
                            bpm = response.metadata?.bpm?.toFloat(),
                            duration = audd?.spotify?.duration_ms?.toInt(),
                            spotifyUrl = audd?.spotify?.external_urls?.spotify,
                            appleMusicUrl = audd?.apple_music?.url,
                            songLink = audd?.song_link,
                            yandexMusicUrl = response.yandexMusicUrl,
                            youtubeMusicUrl = response.youtubeMusicUrl,
                            mood = response.metadata?.mood?.moodText ?: response.metadata?.mood_category,
                            instruments = response.metadata?.instruments
                        )
                        withContext(Dispatchers.IO) {
                            trackDao.insert(track)
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("MusicViewModel", "Ошибка при распознавании музыки", e)
                _recognitionState.value = RecognitionState.ERROR
            }
        }
    }

    fun playPreview() {
        previewUrl?.let { url ->
            viewModelScope.launch(Dispatchers.Main) {
                try {
                    if (player == null) {
                        player = ExoPlayer.Builder(MusicRecognizerApp.appContext).build().apply {
                            addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    if (state == Player.STATE_ENDED) {
                                        _isPlaying.value = false
                                    }
                                }
                            })
                        }
                    }

                    player?.apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        play()
                        _isPlaying.value = true
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Ошибка при воспроизведении превью", e)
                }
            }
        }
    }

    fun stopPreview() {
        player?.stop()
        _isPlaying.value = false
    }

    fun releasePlayer() {
        player?.release()
        player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    fun loadLibraryTracks() {
        viewModelScope.launch {
            trackDao.getAllTracks().collect { tracks ->
                _libraryTracks.value = tracks
            }
        }
    }

    fun resetRecognition() {
        _recognitionState.value = RecognitionState.IDLE
        _recognitionResult.value = null
    }

    suspend fun deleteTrack(track: RecognizedTrack) {
        withContext(Dispatchers.IO) {
            trackDao.delete(track)
        }
        loadLibraryTracks()
    }
}