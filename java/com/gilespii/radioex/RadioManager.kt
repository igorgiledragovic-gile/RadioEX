package com.gilespii.radioex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.gilespii.radioex.audio.EqualizerManager
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(UnstableApi::class)
object RadioManager {
    var player: ExoPlayer? = null
    var currentStation: RadioStation? = null
    var nowPlayingText: String? = null
    var currentTrackInfo: TrackInfo? = null
        private set
    var lastIcyTitle: String? = null
        private set

    var stationList: List<RadioStation> = emptyList()
    var currentIndex: Int = 0

    fun clearCurrentTrackInfo() {
        currentTrackInfo = null
        lastIcyTitle = null
    }

    // Singleton main thread handler
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private val metadataListeners = CopyOnWriteArrayList<(TrackInfo) -> Unit>()
    private val stationChangeListeners = CopyOnWriteArrayList<(RadioStation, Int) -> Unit>()

    private var appContext: Context? = null

    // Cache for station logo byte arrays
    // LruCache limited to 20 entries to prevent unbounded memory growth
    private val logoBytesCache = LruCache<Int, ByteArray>(20)

    fun getStationLogoBytes(context: Context, resId: Int): ByteArray? {
        if (resId == 0) return null
        logoBytesCache.get(resId)?.let { return it }
        return try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return null
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            bitmap.recycle()
            logoBytesCache.put(resId, bytes)
            bytes
        } catch (e: Exception) {
            null
        }
    }

    fun addStationChangeListener(listener: (RadioStation, Int) -> Unit) {
        if (!stationChangeListeners.contains(listener)) {
            stationChangeListeners.add(listener)
        }
    }

    fun removeStationChangeListener(listener: (RadioStation, Int) -> Unit) {
        stationChangeListeners.remove(listener)
    }

    fun addMetadataListener(listener: (TrackInfo) -> Unit) {
        if (!metadataListeners.contains(listener)) {
            metadataListeners.add(listener)
        }
    }

    fun removeMetadataListener(listener: (TrackInfo) -> Unit) {
        metadataListeners.remove(listener)
    }

    fun updateNowPlaying(trackInfo: TrackInfo) {
        currentTrackInfo = trackInfo
        val station = currentStation
        val stationName = station?.name ?: "RadioEX"
        val title = if (trackInfo.title.isNotBlank()) trackInfo.title else stationName
        val artist = if (trackInfo.artist.isNotBlank()) trackInfo.artist else stationName
        nowPlayingText = if (trackInfo.artist.isNotBlank() && trackInfo.title.isNotBlank()) {
            "${trackInfo.artist} - ${trackInfo.title}"
        } else {
            title
        }

        // Notify ExoPlayer so MediaSession and system Now Playing / Quick Settings update immediately
        player?.let { p ->
            val context = appContext
            val logoBytes = if (context != null && station != null) getStationLogoBytes(context, station.imageResId) else null
            val updatedMeta = MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(stationName)
                .setArtist(artist)
                .setAlbumTitle(stationName)
                // DISPLAY_SUBTITLE: launcher ocekuje non-null subtitle (stari kod je
                // slao makar ""), bez njega Now Playing kartica se ne prikazuje.
                .setSubtitle(if (trackInfo.artist.isNotBlank()) trackInfo.artist else stationName)
                .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                .setIsPlayable(true)
                // OBAVEZNO: bez ovoga Media3 NE salje METADATA_KEY_DURATION u framework
                // metadata (live stream = TIME_UNSET), launcher ga cita kao -1 i tretira
                // sesiju kao nevalidnu -> Now Playing kartica se ne prikazuje.
                .setDurationMs(0L)
                .apply {
                    if (logoBytes != null) {
                        setArtworkData(logoBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }
                }
                .build()

            mainHandler.post {
                p.playlistMetadata = updatedMeta
            }
        }

        mainHandler.post {
            metadataListeners.forEach { it.invoke(trackInfo) }
        }
    }

    fun updateNowPlaying(text: String) {
        updateNowPlaying(TrackInfo(text, "", null))
    }

    fun getPlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext
        if (player == null) {
            val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(
                    mapOf(
                        "Icy-MetaData" to "1",
                        "Accept" to "*/*"
                    )
                )

            val mediaSourceFactory = DefaultMediaSourceFactory(context.applicationContext)
                .setDataSourceFactory(dataSourceFactory)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000,  // minBufferMs: 30 seconds for stable streaming
                    120000, // maxBufferMs: 2 minutes max buffer
                    5000,   // bufferForPlaybackMs: 5 seconds before starting
                    10000   // bufferForPlaybackAfterRebufferMs: 10 seconds after rebuffer
                )
                .setBackBuffer(30000, true)  // 30 second back buffer for seeking
                .setPrioritizeTimeOverSizeThresholds(false)  // Prioritize stability over speed
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            player = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build()

            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val station = currentStation
                    val url = station?.streamUrl ?: "unknown"
                    android.util.Log.e("RadioManager", "Player error for station ${station?.name} (URL: $url): ${error.message}", error)
                    
                    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                        
                        mainHandler.post {
                            updateNowPlaying("Station Unavailable (404)")
                        }
                    }
                }
            })

            // Centralni listener za in-stream ICY metadata (TDI, Hit FM, RSG, Play, Karolina...)
            player?.addAnalyticsListener(object : AnalyticsListener {
                override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {
                    val station = currentStation ?: return
                    for (i in 0 until metadata.length()) {
                        val rawText = when (val entry = metadata.get(i)) {
                            is IcyInfo -> entry.title?.trim().orEmpty()
                            is TextInformationFrame -> (entry.description ?: entry.value).orEmpty().trim()
                            else -> ""
                        }
                        if (rawText.isNotBlank() && rawText != "Loading..." && rawText != lastIcyTitle) {
                            lastIcyTitle = rawText
                            val parsed = MetadataHelper.parseArtistTitle(rawText)
                            android.util.Log.d("RadioManager", "ICY metadata received for ${station.name}: $rawText -> ${parsed.artist} - ${parsed.title}")
                            mainHandler.post {
                                updateNowPlaying(parsed)
                            }
                        }
                    }
                }
            })
        }
        return player!!
    }

    fun playStation(context: Context, station: RadioStation) {
        val exoPlayer = getPlayer(context)

        if (exoPlayer.isPlaying && currentStation?.id == station.id) {
            return
        }

        clearCurrentTrackInfo()
        lastIcyTitle = null

        currentStation = station
        nowPlayingText = station.name

        val index = stationList.indexOfFirst { it.id == station.id }
        if (index != -1) {
            currentIndex = index
        }

        // Save to listening history
        HistoryRepository.addEntry(
            context,
            station.id,
            station.name,
            station.imageResId,
            nowPlayingText
        )

        // Sync TV Home Screen channel and Watch Next recommendation
        com.gilespii.radioex.tv.TvChannelManager.syncChannels(context)
        com.gilespii.radioex.tv.TvChannelManager.updateWatchNext(context, station)

        stationChangeListeners.forEach { it.invoke(station, currentIndex) }

        // startForegroundService sa ACTION_START: servis postaje foreground i
        // objavljuje sesiju/notifikaciju (VRadio flow).
        val sIntent = Intent(context.applicationContext, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_START
        }
        ContextCompat.startForegroundService(context.applicationContext, sIntent)

        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        val logoBytes = getStationLogoBytes(context, station.imageResId)
        val artworkUri = if (station.imageResId != 0) {
            Uri.parse("android.resource://${context.packageName}/${station.imageResId}")
        } else {
            null
        }

        val meta = MediaMetadata.Builder()
            .setTitle(station.name)
            .setDisplayTitle(station.name)
            .setArtist(station.name)
            .setAlbumTitle("RadioEX")
            .setSubtitle(station.name)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setIsPlayable(true)
            // Videti napomenu u updateNowPlaying — launcher zahteva DURATION >= 0.
            .setDurationMs(0L)
            .apply {
                if (logoBytes != null) {
                    setArtworkData(logoBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                if (artworkUri != null) {
                    setArtworkUri(artworkUri)
                }
            }
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId("currsong")
            .setUri(station.streamUrl)
            .setMediaMetadata(meta)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.playlistMetadata = meta
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun playNextStation(context: Context) {
        if (stationList.isEmpty()) return

        currentIndex = (currentIndex + 1) % stationList.size
        val nextStation = stationList[currentIndex]
        playStation(context, nextStation)
    }

    fun playPreviousStation(context: Context) {
        if (stationList.isEmpty()) return

        currentIndex = if (currentIndex - 1 < 0) {
            stationList.size - 1
        } else {
            currentIndex - 1
        }
        val prevStation = stationList[currentIndex]
        playStation(context, prevStation)
    }

    fun stop(context: Context? = null) {
        player?.pause()
        if (context != null) {
            val i = Intent(context.applicationContext, RadioPlaybackService::class.java).apply {
                action = RadioPlaybackService.ACTION_STOP
            }
            try {
                context.applicationContext.startService(i)
            } catch (_: Exception) {
                try {
                    ContextCompat.startForegroundService(context.applicationContext, i)
                } catch (_: Exception) {}
            }
        }
    }

    fun kill(context: Context) {
        player?.stop()
        nowPlayingText = null
        currentStation = null
        val i = Intent(context.applicationContext, RadioPlaybackService::class.java).apply {
            action = RadioPlaybackService.ACTION_KILL
        }
        try {
            context.applicationContext.startService(i)
        } catch (_: Exception) {
            try {
                ContextCompat.startForegroundService(context.applicationContext, i)
            } catch (_: Exception) {}
        }
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }
}
