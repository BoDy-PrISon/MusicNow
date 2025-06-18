package com.example.musicnow.api

import com.google.gson.annotations.SerializedName

data class RecognitionResponse(
    val metadata: Metadata?,
    val audd: AuddResult?,
    @SerializedName("yandex_music_url")
    val yandexMusicUrl: String?,
    @SerializedName("youtube_music_url")
    val youtubeMusicUrl: String?
)

data class Metadata(
    @SerializedName("instruments") val instruments: List<String>?,
    val bpm: Double?,
    val genre: String?,
    val confidence: Double?,
    val mood: Mood?,
    @SerializedName("mood_category") val mood_category: String? = null
)

data class AuddResult(
    val artist: String?,
    val title: String?,
    val album: String?,
    val release_date: String?,
    val label: String?,
    val timecode: String?,
    val song_link: String?,
    val apple_music: AppleMusic?,
    val spotify: Spotify?,
    @SerializedName("yandex_music_url")
    val yandexMusicUrl: String?,
    val youtube: Youtube?
)

data class AppleMusic(
    val previews: List<Preview>?,
    val artwork: Artwork?,
    val artistName: String?,
    val url: String?,
    val discNumber: Int?,
    val genreNames: List<String>?,
    val durationInMillis: Long?,
    val releaseDate: String?,
    val name: String?,
    val isrc: String?,
    val albumName: String?,
    val playParams: PlayParams?,
    val trackNumber: Int?,
    val composerName: String?,
    val isAppleDigitalMaster: Boolean?,
    val hasLyrics: Boolean?
)

data class Preview(
    val url: String?
)

data class Artwork(
    val width: Int?,
    val height: Int?,
    val url: String?,
    val bgColor: String?,
    val textColor1: String?,
    val textColor2: String?,
    val textColor3: String?,
    val textColor4: String?
)

data class PlayParams(
    val id: String?,
    val kind: String?
)

data class Spotify(
    val album: SpotifyAlbum?,
    val external_ids: ExternalIds?,
    val popularity: Int?,
    val is_playable: Boolean?,
    val linked_from: Any?,
    val artists: List<SpotifyArtist>?,
    val available_markets: List<String>?,
    val disc_number: Int?,
    val duration_ms: Long?,
    val explicit: Boolean?,
    val external_urls: ExternalUrls?,
    val href: String?,
    val id: String?,
    val name: String?,
    val preview_url: String?,
    val track_number: Int?,
    val uri: String?,
    val type: String?
)

data class SpotifyAlbum(
    val name: String?,
    val artists: List<SpotifyArtist>?,
    val album_group: String?,
    val album_type: String?,
    val id: String?,
    val uri: String?,
    val available_markets: List<String>?,
    val href: String?,
    val images: List<SpotifyImage>?,
    val external_urls: ExternalUrls?,
    val release_date: String?,
    val release_date_precision: String?,
    val genreNames: List<String>?
)

data class SpotifyArtist(
    val name: String?,
    val id: String?,
    val uri: String?,
    val href: String?,
    val external_urls: ExternalUrls?
)

data class SpotifyImage(
    val height: Int?,
    val width: Int?,
    val url: String?
)

data class ExternalIds(
    val isrc: String?
)

data class ExternalUrls(
    val spotify: String?
)

data class Mood(
    @SerializedName("mood_text") val moodText: String?,
    val arousal: Double?,
    val valence: Double?
)

data class Youtube(
    val url: String?
)