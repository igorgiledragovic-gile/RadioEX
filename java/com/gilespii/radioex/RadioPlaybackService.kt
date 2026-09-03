package com.gilespii.radioex

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.Player
import com.gilespii.radioex.audio.EqualizerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioPlaybackService : Service() {

    companion object {
        const val ACTION_START = "com.gilespii.radioex.action.START"
        const val ACTION_STOP = "com.gilespii.radioex.action.STOP"
        const val ACTION_KILL = "com.gilespii.radioex.action.KILL"
        const val ACTION_NEXT = "com.gilespii.radioex.action.NEXT"
        const val ACTION_PREV = "com.gilespii.radioex.action.PREV"
        const val ACTION_UPDATE_METADATA = "com.gilespii.radioex.action.UPDATE_METADATA"
        const val CUSTOM_ACTION_DISMISS = "action_dismiss"
        const val NOTIF_ID = 1
        const val CHANNEL_ID = "radio_media_playback"
        private const val CHANNEL_NAME = "Radio playback"
    }

    private var mediaSession: MediaSessionCompat? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val metadataUpdateHandler = Handler(Looper.getMainLooper())
    private var isServiceActive = true

    // Vreme pocetka reprodukcije — rastuca position u PlaybackState-u.
    private var playbackStartTimeMs: Long = 0L
    private var wasReportingPlaying = false

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var currentAudioSessionId: Int = 0

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    /**
     * AudioFocus Listener: Kada bilo koja druga aplikacija (YouTube, EON, Netflix, Spotify, drugi radio)
     * preuzme audio fokus na televizoru, RadioEX se odmah potpuno gasi kako ne bi smetao i trošio resurse.
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d("RadioPlayback", "onAudioFocusChange: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i("RadioPlayback", "Audio focus lost to another app ($focusChange). Shutting down RadioEX completely.")
                killPlayback()
            }
        }
    }

    /**
     * Receiver za Ambient Mode (Google TV Backdrop / Screensaver).
     * Kada se pokrene prikaz slika u pozadini, osvežava MediaSession i WakeLock
     * kako bi se prikazala signalizacija i sprečilo gašenje televizora zbog neaktivnosti.
     */
    private val ambientModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_DREAMING_STARTED -> {
                    Log.d("RadioPlayback", "Ambient mode / Screensaver started")
                    if (RadioManager.isPlaying()) {
                        ensureWakeLockHeld()
                        mediaSession?.isActive = true
                        updatePlaybackState()
                        updateMetadata()
                    }
                }
                Intent.ACTION_DREAMING_STOPPED -> {
                    Log.d("RadioPlayback", "Ambient mode stopped")
                    if (RadioManager.isPlaying()) {
                        updatePlaybackState()
                        updateMetadata()
                    }
                }
            }
        }
    }

    private fun ensureWakeLockHeld() {
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(10 * 60 * 60 * 1000L)
                Log.d("RadioPlayback", "Playback WakeLock refreshed")
            }
        } catch (e: Exception) {
            Log.w("RadioPlayback", "Failed to refresh WakeLock: ${e.message}")
        }
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val playbackAttributes = AndroidAudioAttributes.Builder()
                        .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                        .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()

                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                        .build()
                }
                val res = am.requestAudioFocus(audioFocusRequest!!)
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
                res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("RadioPlayback", "Failed to request audio focus: ${e.message}", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
        } catch (e: Exception) {
            Log.w("RadioPlayback", "Failed to abandon audio focus: ${e.message}")
        }
    }

    private val metadataUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isServiceActive) return

            val station = RadioManager.currentStation
            if (station != null &&
                station.metadataType != MetadataType.STANDARD &&
                !station.metadataUrl.isNullOrEmpty()
            ) {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val trackInfo = MetadataHelper.getMetadata(
                            station.metadataUrl,
                            station.metadataType
                        )

                        if (trackInfo.title.isBlank() && trackInfo.artist.isBlank()) return@launch

                        RadioManager.updateNowPlaying(trackInfo)
                    } catch (e: Exception) {
                        Log.e("RadioPlayback", "Metadata fetch failed", e)
                    }
                }
            }

            // Signalization & Heartbeat for Google TV Ambient Mode & TV Inactivity Watchdog
            if (RadioManager.isPlaying()) {
                ensureWakeLockHeld()
                mediaSession?.isActive = true
                updatePlaybackState()
            }

            metadataUpdateHandler.postDelayed(this, 30000L)
        }
    }

    private var balkanSocket: BalkanWebSocketManager? = null

    private fun startBalkanSocket(station: RadioStation) {
        stopBalkanSocket()
        if (station.metadataType == MetadataType.WEBSOCKET_BALKAN) {
            val expectedStationId = station.id
            balkanSocket = BalkanWebSocketManager { track ->
                if (RadioManager.currentStation?.id == expectedStationId) {
                    RadioManager.updateNowPlaying(track)
                }
            }
            balkanSocket?.start(station.streamUrl)
        }
    }

    private fun stopBalkanSocket() {
        balkanSocket?.close()
        balkanSocket = null
    }

    private val serviceMetadataListener: (TrackInfo) -> Unit = { _ ->
        updateMetadata()
        updateNotification()
    }

    private val stationChangeListener: (RadioStation, Int) -> Unit = { station, _ ->
        playbackStartTimeMs = 0L
        wasReportingPlaying = false
        startBalkanSocket(station)
        updatePlaybackState()
        updateMetadata()
        updateNotification()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RadioPlayback", "onCreate called. Process: ${android.os.Process.myPid()}")
        isServiceActive = true

        audioManager = getSystemService(AudioManager::class.java)

        // Acquire Locks
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioEX::PlaybackWakeLock").apply {
            acquire(10 * 60 * 60 * 1000L)
        }

        val wifiManager = getSystemService(android.net.wifi.WifiManager::class.java)
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RadioEX::WifiLock").apply {
            acquire()
        }

        // Register Ambient Mode (Daydream / Screensaver) receiver
        try {
            val dreamFilter = IntentFilter().apply {
                addAction(Intent.ACTION_DREAMING_STARTED)
                addAction(Intent.ACTION_DREAMING_STOPPED)
            }
            registerReceiver(ambientModeReceiver, dreamFilter)
        } catch (e: Exception) {
            Log.w("RadioPlayback", "Failed to register ambient mode receiver: ${e.message}")
        }

        ensureChannel()

        val realPlayer = RadioManager.getPlayer(applicationContext)

        // Initialize equalizer
        val initialSessionId = realPlayer.audioSessionId
        if (initialSessionId != 0) {
            currentAudioSessionId = initialSessionId
            EqualizerManager.init(applicationContext, initialSessionId)
        }

        realPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != 0 && audioSessionId != currentAudioSessionId) {
                    currentAudioSessionId = audioSessionId
                    EqualizerManager.init(applicationContext, audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
                updateNotification()
            }
        })

        mediaSession = MediaSessionCompat(this, "RadioEXMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(createSessionActivityPendingIntent())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    requestAudioFocus()
                    RadioManager.player?.play()
                }

                override fun onPause() {
                    RadioManager.player?.pause()
                }

                override fun onStop() {
                    killPlayback()
                }

                override fun onSkipToNext() {
                    RadioManager.playNextStation(applicationContext)
                }

                override fun onSkipToPrevious() {
                    RadioManager.playPreviousStation(applicationContext)
                }

                override fun onSeekTo(pos: Long) {
                    RadioManager.player?.seekTo(pos.coerceAtLeast(0L))
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId == null) return
                    val station = RadioManager.stationList.firstOrNull { it.id.toString() == mediaId }
                        ?: RadioManager.stationList.firstOrNull()
                    if (station != null) {
                        RadioManager.playStation(applicationContext, station)
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    val list = RadioManager.stationList
                    if (list.isEmpty()) return
                    val q = query?.trim()?.lowercase().orEmpty()
                    val station = if (q.isNotEmpty()) {
                        list.firstOrNull { it.name.lowercase().contains(q) } ?: list.first()
                    } else {
                        list.firstOrNull()
                    }
                    if (station != null) {
                        RadioManager.playStation(applicationContext, station)
                    }
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == CUSTOM_ACTION_DISMISS) {
                        killPlayback()
                    }
                }
            })
            setQueue(null)
            isActive = true
        }

        RadioManager.addMetadataListener(serviceMetadataListener)
        RadioManager.addStationChangeListener(stationChangeListener)

        updatePlaybackState()
        updateMetadata()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Radio playback notification"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun startForegroundInternal() {
        try {
            val notif = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIF_ID, notif)
            }
            Log.d("RadioPlayback", "startForeground successful")
        } catch (e: Exception) {
            Log.e("RadioPlayback", "startForeground failed: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Log.w("RadioPlayback", "Foreground start not allowed from background. Stopping service to avoid ANR.")
                stopSelf()
            } else if (e is RuntimeException && e.message?.contains("ForegroundServiceDidNotStartInTimeException") == true) {
                Log.e("RadioPlayback", "Detected potential FGS start timeout. Stopping.")
                stopSelf()
            }
        }
    }

    private fun updateNotification() {
        if (!isServiceActive) return
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.e("RadioPlayback", "Failed to update notification", e)
        }
    }

    private fun updatePlaybackState() {
        val player = RadioManager.player
        val isPlaying = player?.isPlaying == true || player?.playWhenReady == true

        if (isPlaying && !wasReportingPlaying) {
            playbackStartTimeMs = SystemClock.elapsedRealtime()
        }
        wasReportingPlaying = isPlaying
        val position = if (playbackStartTimeMs > 0L) {
            SystemClock.elapsedRealtime() - playbackStartTimeMs
        } else 0L

        val hasError = player?.playerError != null
        val exoState = player?.playbackState ?: Player.STATE_IDLE
        val state = when {
            hasError -> PlaybackStateCompat.STATE_ERROR
            exoState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            exoState == Player.STATE_IDLE -> PlaybackStateCompat.STATE_CONNECTING
            exoState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            )
            .setState(state, position, 1.0f, SystemClock.elapsedRealtime())
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_ACTION_DISMISS,
                    getString(R.string.close),
                    R.drawable.ic_dismiss
                ).build()
            )

        mediaSession?.setPlaybackState(stateBuilder.build())
        Log.d("RadioPlayback", "PlaybackState: state=$state, position=${position}ms, playing=$isPlaying")
    }

    private fun killPlayback() {
        Log.d("RadioPlayback", "killPlayback invoked - shutting down completely")
        isServiceActive = false
        stopBalkanSocket()
        metadataUpdateHandler.removeCallbacks(metadataUpdateRunnable)

        abandonAudioFocus()

        RadioManager.player?.stop()
        RadioManager.currentStation = null
        RadioManager.nowPlayingText = null
        RadioManager.clearCurrentTrackInfo()

        EqualizerManager.release()
        currentAudioSessionId = 0

        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.e("RadioPlayback", "Error releasing locks", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Ucitava i skalira logo stanice za MediaMetadata / notifikaciju.
     * Kvadratna 450x450 (izvorni logoi su kvadratni) — stara radna verzija je
     * koristila istu velicinu; 320x180 sabija logo i izlomi ga.
     */
    private fun loadStationLogo(imageResId: Int): android.graphics.Bitmap? {
        if (imageResId == 0) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, imageResId, options)

            val targetSize = 450
            var sampleSize = 1
            val srcWidth = maxOf(options.outWidth, 1)
            val srcHeight = maxOf(options.outHeight, 1)
            while ((srcWidth / (sampleSize * 2)) >= targetSize &&
                (srcHeight / (sampleSize * 2)) >= targetSize
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val sampled = BitmapFactory.decodeResource(resources, imageResId, decodeOptions)
                ?: return null

            // Cuvamo odnos stranica izvornog loga (kvadrat) — bez izoblicenja.
            val scale = minOf(
                targetSize.toFloat() / sampled.width,
                targetSize.toFloat() / sampled.height
            )
            val outWidth = (sampled.width * scale).toInt().coerceAtLeast(1)
            val outHeight = (sampled.height * scale).toInt().coerceAtLeast(1)

            if (outWidth == sampled.width && outHeight == sampled.height) {
                sampled
            } else {
                val scaled = android.graphics.Bitmap.createScaledBitmap(sampled, outWidth, outHeight, true)
                if (scaled !== sampled) sampled.recycle()
                scaled
            }
        } catch (e: Exception) {
            Log.e("RadioPlayback", "Failed to load station logo resId=$imageResId", e)
            null
        }
    }

    private fun updateMetadata() {
        val station = RadioManager.currentStation ?: return
        val trackInfo = RadioManager.currentTrackInfo
        val stationName = station.name
        val title = if (trackInfo != null && trackInfo.title.isNotBlank()) trackInfo.title else stationName
        val artist = if (trackInfo != null && trackInfo.artist.isNotBlank()) trackInfo.artist else stationName
        val displaySubtitle = if (trackInfo != null && trackInfo.artist.isNotBlank()) trackInfo.artist else ""

        val logoBitmap = loadStationLogo(station.imageResId)

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "station_${station.id}")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, stationName)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, stationName)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, displaySubtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1L)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0L)

        if (station.imageResId != 0) {
            metaBuilder.putString(
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
                "android.resource://$packageName/${station.imageResId}"
            )
        }

        if (logoBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, logoBitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, logoBitmap)
        }

        mediaSession?.setMetadata(metaBuilder.build())
        Log.d(
            "RadioPlayback",
            "Metadata set: title=$title, artist=$artist, hasBitmap=${logoBitmap != null}"
        )
    }

    private fun buildNotification(): Notification {
        val station = RadioManager.currentStation
        val stationName = station?.name ?: "Radio Winamp"
        val trackInfo = RadioManager.currentTrackInfo
        val contentText = if (trackInfo != null && trackInfo.artist.isNotBlank() && trackInfo.title.isNotBlank()) {
            "${trackInfo.artist} - ${trackInfo.title}"
        } else if (trackInfo != null && trackInfo.title.isNotBlank()) {
            trackInfo.title
        } else {
            stationName
        }

        val sessionActivityPendingIntent = createSessionActivityPendingIntent()

        val largeIconBitmap = loadStationLogo(station?.imageResId ?: 0)

        val isPlaying = RadioManager.isPlaying()

        val playPauseIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_STOP else ACTION_START
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            2,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, RadioPlaybackService::class.java).apply {
            action = ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            3,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_radio)
            .setContentTitle(stationName)
            .setContentText(contentText)
            .setContentIntent(sessionActivityPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        mediaSession?.sessionToken?.let { token ->
            builder.setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(false)
            )
        }

        builder.addAction(
            android.R.drawable.ic_media_previous,
            "Previous",
            prevPendingIntent
        )
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPausePendingIntent
        )
        builder.addAction(
            android.R.drawable.ic_media_next,
            "Next",
            nextPendingIntent
        )

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("RadioPlayback", "onStartCommand: action=${intent?.action}, startId=$startId")
        
        startForegroundInternal()

        MediaButtonReceiver.handleIntent(mediaSession, intent)
        when (intent?.action) {
            ACTION_START -> {
                isServiceActive = true
                playbackStartTimeMs = 0L
                wasReportingPlaying = false
                RadioManager.currentStation?.let { startBalkanSocket(it) }
                requestAudioFocus()
                ensureWakeLockHeld()
                updatePlaybackState()
                updateMetadata()
                startForegroundInternal()
                metadataUpdateHandler.removeCallbacks(metadataUpdateRunnable)
                metadataUpdateHandler.postDelayed(metadataUpdateRunnable, 1000L)
            }
            ACTION_NEXT -> {
                requestAudioFocus()
                RadioManager.playNextStation(applicationContext)
            }
            ACTION_PREV -> {
                requestAudioFocus()
                RadioManager.playPreviousStation(applicationContext)
            }
            ACTION_UPDATE_METADATA -> {
                updateMetadata()
                updateNotification()
            }
            ACTION_STOP -> {
                abandonAudioFocus()
                RadioManager.player?.pause()
                updatePlaybackState()
                updateNotification()
            }
            ACTION_KILL -> {
                killPlayback()
            }
        }

        return START_STICKY
    }

    private fun createSessionActivityPendingIntent(): PendingIntent {
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val optionsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val opts = ActivityOptions.makeBasic()
                // In Android 14+ (UPSIDE_DOWN_CAKE), only setPendingIntentCreatorBackgroundActivityStartMode
                // is valid when CREATING a PendingIntent. setPendingIntentBackgroundActivityStartMode is only for SENDING.
                opts.setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                opts.toBundle()
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        return try {
            PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                optionsBundle
            )
        } catch (_: Throwable) {
            PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onDestroy() {
        Log.d("RadioPlayback", "Service onDestroy called")
        isServiceActive = false
        playbackStartTimeMs = 0L
        wasReportingPlaying = false
        stopBalkanSocket()
        metadataUpdateHandler.removeCallbacks(metadataUpdateRunnable)
        RadioManager.removeMetadataListener(serviceMetadataListener)
        RadioManager.removeStationChangeListener(stationChangeListener)

        try {
            unregisterReceiver(ambientModeReceiver)
        } catch (_: Exception) {}

        abandonAudioFocus()

        serviceScope.cancel()

        EqualizerManager.release()
        currentAudioSessionId = 0

        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.e("RadioPlayback", "Error releasing locks", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
