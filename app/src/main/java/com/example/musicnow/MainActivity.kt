package com.example.musicnow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.musicnow.api.MusicRecognitionService
import com.example.musicnow.api.RecognitionResponse
import com.example.musicnow.ui.theme.MusicNowTheme
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow

class MainActivity : ComponentActivity() {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private lateinit var audioFilePath: String
    private lateinit var viewModel: MusicViewModel

    // Глобальное состояние темы
    private val _isDarkTheme = mutableStateOf(false)
    val isDarkTheme: State<Boolean> get() = _isDarkTheme

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Необходимы разрешения для записи аудио", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MusicViewModel::class.java]
        
        // Создаем директорию для аудиофайлов, если она не существует
        val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "MusicRecognizer")
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        audioFilePath = "${audioDir.absolutePath}/recording.m4a"

        // Запрашиваем необходимые разрешения
        requestPermissions()

        setContent {
            val isDark = _isDarkTheme.value
            MusicNowTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicNowApp(
                        viewModel = viewModel,
                        onStartRecording = { startRecording() },
                        onStopRecording = { stopRecordingAndRecognize() },
                        isDarkTheme = isDark,
                        onThemeChange = { _isDarkTheme.value = it }
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения на запись аудио", Toast.LENGTH_SHORT).show()
            return
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)  // API 31+ (новый конструктор)
        } else {
            MediaRecorder()      // API <31 (старый конструктор, deprecated)
        }.apply {  // <- Добавьте контекст
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000) // 128 kbps
            setAudioSamplingRate(44100)    // 44.1 kHz
            setOutputFile(audioFilePath)
            setMaxDuration(30000) // 30 секунд максимум

            try {
                prepare()
                start()
                isRecording = true
                viewModel.setRecordingState(true)

                // Автоматически останавливаем запись через 30 секунд
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isRecording) {
                        stopRecordingAndRecognize()
                    }
                }, 30000)

            } catch (e: IOException) {
                Log.e("MainActivity", "Ошибка при подготовке MediaRecorder", e)
                Toast.makeText(this@MainActivity, "Ошибка при записи: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecordingAndRecognize() {
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                viewModel.setRecordingState(false)
                
                // Начинаем процесс распознавания
                viewModel.recognizeMusic(audioFilePath, this)
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Ошибка при остановке записи", e)
                Toast.makeText(this, "Ошибка при остановке записи: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaRecorder = null
        viewModel.releasePlayer()
    }
}

@Composable
fun MusicNowApp(
    viewModel: MusicViewModel,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.RECOGNITION) }
    var selectedTrack by remember { mutableStateOf<RecognizedTrack?>(null) }
    val isRecording by viewModel.isRecording.collectAsState()
    val recognitionState by viewModel.recognitionState.collectAsState()
    val recognitionResult by viewModel.recognitionResult.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(currentScreen = currentScreen, onScreenSelected = {
                currentScreen = it
                selectedTrack = null
                if (it == AppScreen.RECOGNITION) viewModel.resetRecognition()
            })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                AppScreen.RECOGNITION -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (recognitionState) {
                            RecognitionState.IDLE, RecognitionState.ERROR -> {
                                RecordButton(
                                    isRecording = isRecording,
                                    onStartRecording = onStartRecording,
                                    onStopRecording = onStopRecording
                                )
                            }
                            RecognitionState.RECORDING -> {
                                RecordButton(
                                    isRecording = true,
                                    onStartRecording = onStartRecording,
                                    onStopRecording = onStopRecording
                                )
                            }
                            RecognitionState.RECOGNIZING -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(100.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 8.dp
                                    )
                                    Text(
                                        text = "Распознаем трек...",
                                        modifier = Modifier.padding(top = 24.dp),
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            RecognitionState.SUCCESS -> {
                                recognitionResult?.let { result ->
                                    SongDetailsCard(
                                        result = result,
                                        isPlaying = isPlaying,
                                        onPlayPreview = { viewModel.playPreview() },
                                        onStopPreview = { viewModel.stopPreview() },
                                        onBack = {
                                            viewModel.resetRecognition()
                                            currentScreen = AppScreen.RECOGNITION
                                        }
                                    )
                                }
                            }
                        }
                        if (recognitionState == RecognitionState.ERROR) {
                            Text(
                                text = "Ошибка при распознавании. Попробуйте еще раз.",
                                color = Color.Red,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                            )
                        }
                    }
                }
                AppScreen.LIBRARY -> {
                    val tracks by viewModel.libraryTracks.collectAsState()
                    if (selectedTrack == null) {
                        LibraryScreen(tracks) { track ->
                            selectedTrack = track
                            currentScreen = AppScreen.SONG_DETAILS
                        }
                    } else {
                        LibraryTrackDetailsCard(
                            track = selectedTrack!!,
                            onBack = {
                                selectedTrack = null
                                currentScreen = AppScreen.LIBRARY
                            }
                        )
                    }
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(viewModel, isDarkTheme, onThemeChange)
                }
                AppScreen.SONG_DETAILS -> {
                    // Не используется напрямую, подробная карточка трека из библиотеки
                    selectedTrack?.let { track ->
                        LibraryTrackDetailsCard(
                            track = track,
                            onBack = {
                                selectedTrack = null
                                currentScreen = AppScreen.LIBRARY
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = if (isRecording) onStopRecording else onStartRecording,
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "Остановить запись" else "Начать запись",
                modifier = Modifier.size(48.dp)
            )
        }
        
        Text(
            text = if (isRecording) "Нажмите, чтобы остановить" else "Нажмите, чтобы начать запись",
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 16.sp
        )
        
        if (isRecording) {
            Text(
                text = "Запись до 30 секунд...",
                modifier = Modifier.padding(top = 8.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SongDetailsCard(
    result: RecognitionResponse,
    isPlaying: Boolean,
    onPlayPreview: () -> Unit,
    onStopPreview: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val songInfo = result.audd
    val spotifyInfo = songInfo?.spotify
    val appleMusic = songInfo?.apple_music
    val metadata = result.metadata
    val imageUrl = appleMusic?.artwork?.url?.replace("{w}", "640")?.replace("{h}", "640")
        ?: spotifyInfo?.album?.images?.firstOrNull()?.url
    val genre = spotifyInfo?.album?.genreNames?.firstOrNull() ?: metadata?.genre
    val confidence = metadata?.confidence
    val bpm = metadata?.bpm
    val durationMs = spotifyInfo?.duration_ms
    val minutes = durationMs?.div(60000) ?: 0
    val seconds = durationMs?.rem(60000)?.div(1000) ?: 0
    val previewUrl = spotifyInfo?.preview_url ?: appleMusic?.previews?.firstOrNull()?.url
    val songLink = songInfo?.song_link
    val spotifyUrl = spotifyInfo?.external_urls?.spotify
    val yandexMusicUrl = result.yandexMusicUrl
    val appleMusicUrl = appleMusic?.url
    val youtubeMusicUrl = result.youtubeMusicUrl
    var showCoverDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Обложка альбома
            if (imageUrl != null) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .clickable { showCoverDialog = true }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(
                            ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build()
                        ),
                        contentDescription = "Album Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (showCoverDialog && imageUrl != null) {
                AlertDialog(
                    onDismissRequest = { showCoverDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showCoverDialog = false }) { Text("Закрыть") }
                    },
                    text = {
                        Image(
                            painter = rememberAsyncImagePainter(imageUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )
                    }
                )
            }
            // 2. Название трека
            Text(
                text = songInfo?.title ?: "Неизвестный трек",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            // 3. Исполнитель
            Text(
                text = songInfo?.artist ?: "Неизвестный исполнитель",
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 4. Альбом
            Text(
                text = "Альбом: ${songInfo?.album ?: "Неизвестно"}",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            // 5. Дата релиза
            Text(
                text = "Дата релиза: ${songInfo?.release_date ?: "Неизвестно"}",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 6. Жанр (точность)
            if (genre != null) {
                val genreText = if (confidence != null) {
                    "$genre (точность: ${String.format("%.1f%%", confidence * 100)})"
                } else genre
                Text(
                    text = "Жанр: $genreText",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Добавляем настроение
            val mood = metadata?.mood_category
            if (!mood.isNullOrBlank()) {
                Text(
                    text = "Настроение: $mood",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 7. BPM
            if (bpm != null) {
                Text(
                    text = "BPM: ${String.format("%.1f", bpm)}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 8. Длительность
            if (durationMs != null) {
                Text(
                    text = "Длительность: $minutes:${seconds.toString().padStart(2, '0')}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Добавляем инструменты
            val instruments = metadata?.instruments
            if (!instruments.isNullOrEmpty()) {
                Text(
                    text = "Инструменты: ${instruments.joinToString()}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 9. Ссылки
            val musicServices = listOfNotNull(
                if (!youtubeMusicUrl.isNullOrEmpty()) Triple("YouTube", youtubeMusicUrl, Color(0xFFFF0000)) else null,
                if (!yandexMusicUrl.isNullOrEmpty()) Triple("Яндекс", yandexMusicUrl, Color(0xFFFFCC00)) else null,
                if (!spotifyUrl.isNullOrEmpty()) Triple("Spotify", spotifyUrl, Color(0xFF1DB954)) else null,
                if (!appleMusicUrl.isNullOrEmpty()) Triple("Apple", appleMusicUrl, Color(0xFF007AFF)) else null
            )
            
            if (musicServices.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height((48 * ((musicServices.size + 1) / 2)).dp)
                ) {
                    items(musicServices) { (name, url, color) ->
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (name == "Яндекс") Color.Black else Color.White
                            )
                        }
                    }
                }
                
                // Кнопка 'Поделиться' с выбором сервиса
                val shareOptions = listOfNotNull(
                    if (!youtubeMusicUrl.isNullOrEmpty()) "YouTube Music" to youtubeMusicUrl else null,
                    if (!yandexMusicUrl.isNullOrEmpty()) "Яндекс.Музыка" to yandexMusicUrl else null,
                    if (!spotifyUrl.isNullOrEmpty()) "Spotify" to spotifyUrl else null,
                    if (!appleMusicUrl.isNullOrEmpty()) "Apple Music" to appleMusicUrl else null
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                var selectedShare by remember { mutableStateOf<Pair<String, String>?>(null) }
                Box {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Поделиться")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        shareOptions.forEach { (label, url) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    selectedShare = label to url
                                }
                            )
                        }
                    }
                }
                selectedShare?.let { (label, url) ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    selectedShare = null
                }
            }
            // Кнопка прослушивания превью (если есть)
            if (!previewUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { if (isPlaying) onStopPreview() else onPlayPreview() }) {
                    Text(text = if (isPlaying) "Стоп" else "Прослушать")
                }
            }
            Button(onClick = onBack) { Text("Назад") }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(tracks: List<RecognizedTrack>, onTrackClick: (RecognizedTrack) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("По дате") }
    var genreFilter by remember { mutableStateOf("Все жанры") }
    val genres = remember(tracks) {
        listOf("Все жанры") + tracks.mapNotNull { it.genre }.distinct().sorted()
    }
    val filteredTracks = tracks
        .filter {
            (it.title?.contains(searchQuery, ignoreCase = true) == true ||
             it.artist?.contains(searchQuery, ignoreCase = true) == true)
            && (genreFilter == "Все жанры" || it.genre == genreFilter)
        }
        .let {
            when (sortOption) {
                "По дате" -> it // по умолчанию из базы — по дате
                "По названию" -> it.sortedBy { t -> t.title ?: "" }
                "По исполнителю" -> it.sortedBy { t -> t.artist ?: "" }
                else -> it
            }
        }
    val context = LocalContext.current
    val viewModel: MusicViewModel = viewModel()
    var trackToDelete by remember { mutableStateOf<RecognizedTrack?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Библиотека треков",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { sortMenuExpanded = true }) { Text(sortOption) }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("По дате") }, onClick = { sortOption = "По дате"; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text("По названию") }, onClick = { sortOption = "По названию"; sortMenuExpanded = false })
                    DropdownMenuItem(text = { Text("По исполнителю") }, onClick = { sortOption = "По исполнителю"; sortMenuExpanded = false })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            var genreMenuExpanded by remember { mutableStateOf(false) }
            Box {
                Button(onClick = { genreMenuExpanded = true }) { Text(genreFilter) }
                DropdownMenu(expanded = genreMenuExpanded, onDismissRequest = { genreMenuExpanded = false }) {
                    genres.forEach { genre ->
                        DropdownMenuItem(text = { Text(genre) }, onClick = { genreFilter = genre; genreMenuExpanded = false })
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (filteredTracks.isEmpty()) {
            Text("Нет распознанных треков.", color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredTracks, key = { it.id }) { track ->
                    val dismissState = rememberDismissState(
                        confirmStateChange = {
                            if (it == DismissValue.DismissedToStart || it == DismissValue.DismissedToEnd) {
                                trackToDelete = track
                                showDialog = true
                                false // не удаляем сразу
                            } else true
                        }
                    )
                    SwipeToDismiss(
                        state = dismissState,
                        directions = setOf(DismissDirection.EndToStart, DismissDirection.StartToEnd),
                        background = {
                            val color = when (dismissState.dismissDirection) {
                                DismissDirection.StartToEnd, DismissDirection.EndToStart -> Color.Red
                                null -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                if (color == Color.Red) {
                                    Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissContent = {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .fillMaxHeight()
                                    .clickable { onTrackClick(track) }
                                    .animateItemPlacement(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!track.coverUrl.isNullOrEmpty()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(track.coverUrl),
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFE0E0E0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MusicNote,
                                                contentDescription = "Нет обложки",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title ?: "Неизвестный трек", fontWeight = FontWeight.Bold)
                                        Text(track.artist ?: "Неизвестный исполнитель", fontSize = 14.sp, color = Color.Gray)
                                        Text(track.album ?: "", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    )
                    Divider()
                }
            }
        }
        if (showDialog && trackToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Удалить трек?") },
                text = { Text("Вы действительно хотите удалить этот трек из библиотеки?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        trackToDelete?.let { track ->
                            coroutineScope.launch {
                                viewModel.deleteTrack(track)
                            }
                        }
                        trackToDelete = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        trackToDelete = null
                    }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
fun LibraryTrackDetailsCard(track: RecognizedTrack, onBack: () -> Unit) {
    var showCoverDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Обложка альбома
            if (!track.coverUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray)
                        .clickable { showCoverDialog = true }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(track.coverUrl),
                        contentDescription = "Album Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (showCoverDialog && !track.coverUrl.isNullOrEmpty()) {
                AlertDialog(
                    onDismissRequest = { showCoverDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showCoverDialog = false }) { Text("Закрыть") }
                    },
                    text = {
                        Image(
                            painter = rememberAsyncImagePainter(track.coverUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Fit
                        )
                    }
                )
            }
            // 2. Название трека
            Text(
                text = track.title ?: "Неизвестный трек",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            // 3. Исполнитель
            Text(
                text = track.artist ?: "Неизвестный исполнитель",
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 4. Альбом
            Text(
                text = "Альбом: ${track.album ?: "Неизвестно"}",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            // 5. Дата релиза
            Text(
                text = "Дата релиза: ${track.releaseDate ?: "Неизвестно"}",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 6. Жанр (точность)
            if (!track.genre.isNullOrEmpty()) {
                val genreText = if (track.confidence != null) {
                    "${track.genre} (точность: ${String.format("%.1f%%", track.confidence * 100)})"
                } else track.genre
                Text(
                    text = "Жанр: $genreText",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (!track.mood.isNullOrBlank()) {
                Text(
                    text = "Настроение: ${track.mood}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 7. BPM
            if (track.bpm != null) {
                Text(
                    text = "BPM: ${String.format("%.1f", track.bpm)}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 8. Длительность
            if (track.duration != null) {
                val minutes = track.duration / 60000
                val seconds = (track.duration % 60000) / 1000
                Text(
                    text = "Длительность: $minutes:${seconds.toString().padStart(2, '0')}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Добавляем инструменты для сохраненного трека
            if (!track.instruments.isNullOrEmpty()) {
                Text(
                    text = "Инструменты: ${track.instruments.joinToString()}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 9. Ссылки
            val musicServices = listOfNotNull(
                if (!track.youtubeMusicUrl.isNullOrEmpty()) Triple("YouTube", track.youtubeMusicUrl, Color(0xFFFF0000)) else null,
                if (!track.yandexMusicUrl.isNullOrEmpty()) Triple("Яндекс", track.yandexMusicUrl, Color(0xFFFFCC00)) else null,
                if (!track.spotifyUrl.isNullOrEmpty()) Triple("Spotify", track.spotifyUrl, Color(0xFF1DB954)) else null,
                if (!track.appleMusicUrl.isNullOrEmpty()) Triple("Apple", track.appleMusicUrl, Color(0xFF007AFF)) else null
            )
            
            if (musicServices.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height((48 * ((musicServices.size + 1) / 2)).dp)
                ) {
                    items(musicServices) { (name, url, color) ->
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (name == "Яндекс") Color.Black else Color.White
                            )
                        }
                    }
                }
                
                // Кнопка 'Поделиться' с выбором сервиса
                val shareOptions = listOfNotNull(
                    if (!track.youtubeMusicUrl.isNullOrEmpty()) "YouTube Music" to track.youtubeMusicUrl else null,
                    if (!track.yandexMusicUrl.isNullOrEmpty()) "Яндекс.Музыка" to track.yandexMusicUrl else null,
                    if (!track.spotifyUrl.isNullOrEmpty()) "Spotify" to track.spotifyUrl else null,
                    if (!track.appleMusicUrl.isNullOrEmpty()) "Apple Music" to track.appleMusicUrl else null
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                var selectedShare by remember { mutableStateOf<Pair<String, String>?>(null) }
                Box {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Поделиться")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        shareOptions.forEach { (label, url) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    expanded = false
                                    selectedShare = label to url
                                }
                            )
                        }
                    }
                }
                selectedShare?.let { (label, url) ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                    selectedShare = null
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Назад") }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: MusicViewModel, isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(SettingsManager.getBaseUrl(context)) }
    var saved by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Настройки", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; saved = false },
            label = { Text("Base URL сервера") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            viewModel.updateBaseUrl(context, url)
            saved = true
        }) {
            Text("Сохранить")
        }
        if (saved) {
            Text("Сохранено!", color = Color.Green, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Тёмная тема", modifier = Modifier.weight(1f))
            Switch(checked = isDarkTheme, onCheckedChange = onThemeChange)
        }
    }
}

@Composable
fun BottomNavigationBar(currentScreen: AppScreen, onScreenSelected: (AppScreen) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentScreen == AppScreen.RECOGNITION,
            onClick = { onScreenSelected(AppScreen.RECOGNITION) },
            icon = { Icon(Icons.Default.Mic, contentDescription = "Распознавание") },
            label = { Text("Распознавание") }
        )
        NavigationBarItem(
            selected = currentScreen == AppScreen.LIBRARY,
            onClick = { onScreenSelected(AppScreen.LIBRARY) },
            icon = { Icon(Icons.Default.Stop, contentDescription = "Библиотека") },
            label = { Text("Библиотека") }
        )
        NavigationBarItem(
            selected = currentScreen == AppScreen.SETTINGS,
            onClick = { onScreenSelected(AppScreen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
            label = { Text("Настройки") }
        )
    }
}

// Экран приложения
enum class AppScreen {
    RECOGNITION, LIBRARY, SETTINGS, SONG_DETAILS
}