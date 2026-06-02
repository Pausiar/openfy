package com.pausiar.openfy

import android.app.Application
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pausiar.openfy.data.local.OpenfyDatabase
import com.pausiar.openfy.data.remote.spotify.SpotifyProxyRemoteDataSource
import com.pausiar.openfy.data.remote.spotify.SpotifyProxyService
import com.pausiar.openfy.data.remote.youtube.YouTubeApiService
import com.pausiar.openfy.data.remote.youtube.YouTubeInnertubeDataSource
import com.pausiar.openfy.data.remote.youtube.YouTubeRemoteDataSource
import com.pausiar.openfy.data.repository.MusicRepository
import com.pausiar.openfy.data.repository.MusicRepositoryImpl
import com.pausiar.openfy.data.repository.SettingsRepository
import com.pausiar.openfy.data.repository.SettingsRepositoryImpl
import com.pausiar.openfy.domain.usecases.DetectLinkUseCase
import com.pausiar.openfy.domain.usecases.ExportLibraryUseCase
import com.pausiar.openfy.domain.usecases.ImportLibraryDumpUseCase
import com.pausiar.openfy.domain.usecases.ImportLocalTracksUseCase
import com.pausiar.openfy.domain.usecases.ImportPlaylistUseCase
import com.pausiar.openfy.domain.usecases.ObservePlaylistDetailUseCase
import com.pausiar.openfy.domain.usecases.ObservePlaylistsUseCase
import com.pausiar.openfy.playback.NewPipeDownloader
import com.pausiar.openfy.playback.YouTubeStreamResolver
import com.pausiar.openfy.player.controller.PlayerConnection
import org.schabi.newpipe.extractor.NewPipe
import com.pausiar.openfy.utils.linkparser.LinkParser
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class AppContainer(
    application: Application,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
        )
        .build()

    init {
        NewPipe.init(NewPipeDownloader(okHttpClient))
    }

    private val database = Room.databaseBuilder(
        application,
        OpenfyDatabase::class.java,
        "openfy.db",
    ).fallbackToDestructiveMigration().build()

    private val linkParser = LinkParser()

    private val youtubeService = createService(
        baseUrl = "https://www.googleapis.com/",
        serviceClass = YouTubeApiService::class.java,
    )

    private val spotifyProxyService = BuildConfig.SPOTIFY_METADATA_BASE_URL
        .takeIf { it.isNotBlank() }
        ?.let { baseUrl ->
            runCatching {
                createService(
                    baseUrl = ensureTrailingSlash(baseUrl),
                    serviceClass = SpotifyProxyService::class.java,
                )
            }.getOrNull()
        }

    private val youtubeInnertubeDataSource = YouTubeInnertubeDataSource(httpClient = okHttpClient)

    private val youtubeRemoteDataSource = YouTubeRemoteDataSource(
        service = youtubeService,
        apiKey = BuildConfig.YOUTUBE_API_KEY,
    )

    private val spotifyRemoteDataSource = SpotifyProxyRemoteDataSource(
        service = spotifyProxyService,
    )

    private val youtubeStreamResolver = YouTubeStreamResolver()

    val musicRepository: MusicRepository = MusicRepositoryImpl(
        appContext = application,
        playlistDao = database.playlistDao(),
        trackDao = database.trackDao(),
        playbackHistoryDao = database.playbackHistoryDao(),
        linkParser = linkParser,
        youtubeRemoteDataSource = youtubeRemoteDataSource,
        youtubeInnertubeDataSource = youtubeInnertubeDataSource,
        spotifyRemoteDataSource = spotifyRemoteDataSource,
    )

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(application)

    val playerConnection = PlayerConnection(
        appContext = application,
        musicRepository = musicRepository,
        settingsRepository = settingsRepository,
        youtubeStreamResolver = youtubeStreamResolver,
    )

    val detectLinkUseCase = DetectLinkUseCase(linkParser)
    val importPlaylistUseCase = ImportPlaylistUseCase(musicRepository)
    val importLocalTracksUseCase = ImportLocalTracksUseCase(musicRepository)
    val observePlaylistsUseCase = ObservePlaylistsUseCase(musicRepository)
    val observePlaylistDetailUseCase = ObservePlaylistDetailUseCase(musicRepository)
    val exportLibraryUseCase = ExportLibraryUseCase(musicRepository, settingsRepository)
    val importLibraryDumpUseCase = ImportLibraryDumpUseCase(musicRepository, settingsRepository)

    private fun <T> createService(baseUrl: String, serviceClass: Class<T>): T {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(serviceClass)
    }

    private fun ensureTrailingSlash(raw: String): String =
        if (raw.endsWith('/')) raw else "$raw/"
}