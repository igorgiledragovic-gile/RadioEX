package com.gilespii.radioex

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gilespii.radioex.adapter.DrawerStationAdapter
import com.gilespii.radioex.ui.SimpleVisualizerView
import com.gilespii.radioex.util.AccessibilityManager
import com.gilespii.radioex.ui.SettingsDialogFragment
import android.util.Log
import com.gilespii.radioex.util.ThemeManager
    import com.bumptech.glide.Glide
    import com.bumptech.glide.load.engine.DiskCacheStrategy
    import android.view.KeyEvent
    import android.view.ViewGroup
    import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import jp.wasabeef.glide.transformations.BlurTransformation
import com.bumptech.glide.request.RequestOptions
import android.graphics.drawable.Drawable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity(), GestureDetector.OnGestureListener {

    companion object {
        const val EXTRA_STATION_INDEX = "STATION_INDEX"
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    private lateinit var player: ExoPlayer
    private var stationList: ArrayList<RadioStation> = arrayListOf()
    private var currentIndex: Int = 0

    private lateinit var ivBackground: ImageView
    private var currentBackgroundResId: Int = 0
    private lateinit var ivLogo: ImageView
    private lateinit var tvStation: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnEq: ImageButton
    private lateinit var btnBack: View
    private lateinit var visualizerContainer: LinearLayout
    private lateinit var cardAlbum: CardView
    private lateinit var ivSwipeLeft: ImageView
    private lateinit var ivSwipeRight: ImageView
    private lateinit var liveDot: View
    private lateinit var tvLiveLabel: TextView
    private lateinit var btnFavorite: View
    private lateinit var ivFavoriteIcon: ImageView
    private lateinit var tvFavoriteLabel: TextView
    private lateinit var chipBitrate: TextView
    private lateinit var chipCodec: TextView
    private var drawerOpenedFrom: View? = null
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rvDrawerStations: RecyclerView
    private lateinit var drawerAdapter: DrawerStationAdapter
    private var allDrawerStations: List<RadioStation> = emptyList()
    
    // Clock
    private lateinit var tvClock: TextView
    private lateinit var tvClockDate: TextView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000) // Update every second
        }
    }
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private lateinit var gestureDetector: GestureDetector
    private var balkanSocket: BalkanWebSocketManager? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            try {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(i)
            } catch (_: Exception) {}
        } else {
            playCurrent(force = true)
        }
    }

    private fun hasNotifPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission()) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.gilespii.radioex.util.AccessibilityManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply locale & theme BEFORE super
        AccessibilityManager.initLocale(this)
        ThemeManager.init(this)
        setTheme(ThemeManager.getCurrentThemeResId(this))
        
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        // Initialize gesture detector
        gestureDetector = GestureDetector(this, this)

        // Bind views
        ivBackground = findViewById(R.id.iv_background)
        ivLogo = findViewById(R.id.iv_station_logo)
        tvStation = findViewById(R.id.tv_station_name)
        tvNowPlaying = findViewById(R.id.tv_song_metadata)
        liveDot = findViewById(R.id.live_dot)
        tvLiveLabel = findViewById(R.id.tv_live_label)
        btnFavorite = findViewById(R.id.btn_favorite)
        ivFavoriteIcon = findViewById(R.id.iv_favorite_icon)
        tvFavoriteLabel = findViewById(R.id.tv_favorite_label)
        chipBitrate = findViewById(R.id.chip_bitrate)
        chipCodec = findViewById(R.id.chip_codec)
        btnPrev = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnEq = findViewById(R.id.btn_eq)
        btnBack = findViewById(R.id.btn_back)
        visualizerContainer = findViewById(R.id.visualizer_container)
        cardAlbum = findViewById(R.id.card_album)
        ivSwipeLeft = findViewById(R.id.iv_swipe_left)
        ivSwipeRight = findViewById(R.id.iv_swipe_right)
        
        // Clock
        tvClock = findViewById(R.id.tv_clock)
        tvClockDate = findViewById(R.id.tv_clock_date)

        player = RadioManager.getPlayer(this)

        stationList = if (RadioManager.stationList.isNotEmpty()) {
            ArrayList(RadioManager.stationList)
        } else {
            RadioRepository.getStations()
        }

        val passedStationId = intent.getIntExtra("station_id", -1).takeIf { it != -1 }
            ?: intent.data?.getQueryParameter("stationId")?.toIntOrNull()

        if (passedStationId != null) {
            val foundIdx = stationList.indexOfFirst { it.id == passedStationId }
            currentIndex = if (foundIdx >= 0) foundIdx else 0
        } else {
            currentIndex = intent.getIntExtra(EXTRA_STATION_INDEX, RadioManager.currentIndex)
                .coerceIn(0, (stationList.size - 1).coerceAtLeast(0))
        }

        RadioManager.stationList = stationList
        RadioManager.currentIndex = currentIndex

        attachVisualizer()
        setupDrawer()
        setupClickListeners()

        ensureNotificationPermissionIfNeeded()
        renderStation()
        updateFavoriteIcon()
        
        if (stationList.isNotEmpty()) {
            val targetStation = stationList[currentIndex]
            if (RadioManager.currentStation?.id != targetStation.id || !player.isPlaying) {
                RadioManager.playStation(this, targetStation)
            }
        }
        
        updatePlayPauseIcon(player.isPlaying)

        // TALAS: entering the player always focuses Play/Pause
        btnPlayPause.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val passedStationId = intent.getIntExtra("station_id", -1).takeIf { it != -1 }
            ?: intent.data?.getQueryParameter("stationId")?.toIntOrNull()

        if (passedStationId != null && stationList.isNotEmpty()) {
            val foundIdx = stationList.indexOfFirst { it.id == passedStationId }
            if (foundIdx >= 0) {
                currentIndex = foundIdx
                RadioManager.currentIndex = currentIndex
                val targetStation = stationList[currentIndex]
                RadioManager.playStation(this, targetStation)
                renderStation()
                updateFavoriteIcon()
                btnPlayPause.requestFocus()
            }
        }
    }

    private val stationChangeListener: (RadioStation, Int) -> Unit = { station, idx ->
        val newIdx = stationList.indexOfFirst { it.id == station.id }.takeIf { it >= 0 } ?: idx
        currentIndex = newIdx.coerceIn(0, (stationList.size - 1).coerceAtLeast(0))
        runOnUiThread {
            renderStation()
            updateFavoriteIcon()
            startMetadataForStation()
            updatePlayPauseIcon(true)
            updateLiveIndicator()
            updateStreamChips()
            // Keep the drawer list highlight in sync while it's open
            drawerAdapter.setCurrentStation(station.id)
            if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(findViewById<View>(R.id.drawer_content))) {
                val fullIdx = allDrawerStations.indexOfFirst { it.id == station.id }
                if (fullIdx >= 0) {
                    rvDrawerStations.post {
                        val holder = rvDrawerStations.findViewHolderForAdapterPosition(fullIdx)
                        holder?.itemView?.requestFocus()
                    }
                }
            }
        }
    }

    private fun updateFavoriteIcon() {
        val station = RadioManager.currentStation ?: return
        val isFav = com.gilespii.radioex.util.FavoritesManager.isFavorite(station.id)

        // Always use star_on (filled) so it's visible, just change color
        ivFavoriteIcon.setImageResource(android.R.drawable.star_on)

        val color = if (isFav) {
            // TALAS: pop bounce when a station becomes favorite
            ivFavoriteIcon.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pop))
            // Favorite -> Red
            androidx.core.content.ContextCompat.getColor(this, R.color.live_red)
        } else {
            // Not favorite -> Theme Accent Color
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(R.attr.themeAccent, typedValue, true)
            typedValue.data
        }
        ivFavoriteIcon.setColorFilter(color)

        tvFavoriteLabel.text = if (isFav) getString(R.string.remove_from_favorites) else getString(R.string.add_to_favorites)
    }

    private fun setupClickListeners() {
        btnPrev.setOnClickListener { 
            animateButton(it)
            RadioManager.playPreviousStation(this) 
        }
        btnNext.setOnClickListener { 
            animateButton(it)
            RadioManager.playNextStation(this) 
        }
        btnPlayPause.setOnClickListener { 
            animatePlayPause()
            togglePlayPause() 
        }
        btnEq.setOnClickListener {
            animateButton(it)
            openEqualizerDialog()
        }
        btnBack.setOnClickListener {
            animateButton(it)
            // If drawer open, close it. Else finish.
            if (drawerLayout.isDrawerOpen(findViewById(R.id.drawer_content))) {
                drawerLayout.closeDrawers()
            } else {
                finish()
            }
        }
        btnFavorite.setOnClickListener {
            val station = RadioManager.currentStation
            if (station != null) {
                com.gilespii.radioex.util.FavoritesManager.toggleFavorite(station.id)
                updateFavoriteIcon()
                animateButton(btnFavorite)
            }
        }
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        rvDrawerStations = findViewById(R.id.rv_drawer_stations)
        val rootPlayerLayout = findViewById<ViewGroup>(R.id.root_player_layout)
        val drawerContent = findViewById<View>(R.id.drawer_content)
        
        // Setup RecyclerView with LinearLayoutManager
        rvDrawerStations.layoutManager = LinearLayoutManager(this)
        
        // ALWAYS use ALL stations for the drawer, regardless of category filter
        allDrawerStations = RadioRepository.getStations()
        
        drawerAdapter = DrawerStationAdapter(allDrawerStations) { radioStation ->
            // On station click - play station directly but KEEP DRAWER OPEN
            // Find index in the full list for RadioManager
            val fullIndex = allDrawerStations.indexOfFirst { it.id == radioStation.id }
            if (fullIndex >= 0) {
                // Update RadioManager with full station list
                RadioManager.stationList = ArrayList(allDrawerStations)
                RadioManager.currentIndex = fullIndex
                currentIndex = fullIndex
                stationList = ArrayList(allDrawerStations)
                
                RadioManager.playStation(this, radioStation)
                // Drawer remains OPEN

                // Update UI + drawer highlight without destroying other viewholders
                drawerAdapter.setCurrentStation(radioStation.id)
                renderStation()
                updateFavoriteIcon()
                updatePlayPauseIcon(true)

                // CRITICAL: Retain focus directly on the clicked station in the drawer list
                rvDrawerStations.post {
                    val holder = rvDrawerStations.findViewHolderForAdapterPosition(fullIndex)
                    holder?.itemView?.requestFocus()
                }
            }
        }
        
        rvDrawerStations.adapter = drawerAdapter

        // Highlight the station currently playing in the background
        RadioManager.currentStation?.let { drawerAdapter.setCurrentStation(it.id) }

        // TALAS: Transparent gradient background (100% left, fading towards right edge)
        val sidebarColor = resolveThemeColor(drawerContent, R.attr.themeSidebar)
        val r = android.graphics.Color.red(sidebarColor)
        val g = android.graphics.Color.green(sidebarColor)
        val b = android.graphics.Color.blue(sidebarColor)
        val startColor = android.graphics.Color.argb(255, r, g, b)
        val midColor = android.graphics.Color.argb(195, r, g, b)
        val endColor = android.graphics.Color.argb(55, r, g, b)
        val drawerGradient = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(startColor, midColor, endColor)
        )
        drawerContent.background = drawerGradient

        // Block background player from receiving focus while drawer is open
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (slideOffset > 0.05f) {
                    rootPlayerLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                rootPlayerLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                focusCurrentStationInDrawer()
            }

            override fun onDrawerClosed(drawerView: View) {
                rootPlayerLayout.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                (drawerOpenedFrom ?: btnPlayPause).requestFocus()
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
        
        // Setup Back button to toggle drawer when pressed
        btnBack.setOnClickListener {
            if (drawerLayout.isDrawerOpen(drawerContent)) {
                drawerLayout.closeDrawers()
            } else {
                finish() // Close activity
            }
        }
    }
    
    private fun focusCurrentStationInDrawer() {
        val playingId = RadioManager.currentStation?.id ?: -1
        val targetIndex = allDrawerStations.indexOfFirst { it.id == playingId }.takeIf { it >= 0 } ?: currentIndex
        val target = targetIndex.coerceIn(0, (allDrawerStations.size - 1).coerceAtLeast(0))

        val lm = rvDrawerStations.layoutManager as? LinearLayoutManager
        val vh = rvDrawerStations.findViewHolderForAdapterPosition(target)
        if (vh != null && vh.itemView.isAttachedToWindow) {
            vh.itemView.requestFocus()
        } else {
            lm?.scrollToPositionWithOffset(target, 40)
            rvDrawerStations.post {
                val holder = rvDrawerStations.findViewHolderForAdapterPosition(target)
                if (holder != null) {
                    holder.itemView.requestFocus()
                } else {
                    rvDrawerStations.getChildAt(0)?.requestFocus()
                }
            }
        }
    }

    /**
     * TALAS: opens the hidden station list with the station playing in the
     * background highlighted, scrolled into view and focused immediately.
     */
    private fun openStationDrawer() {
        val drawerContent = findViewById<View>(R.id.drawer_content)
        val rootPlayerLayout = findViewById<ViewGroup>(R.id.root_player_layout)
        rootPlayerLayout.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        drawerLayout.openDrawer(drawerContent)
        RadioManager.currentStation?.let { drawerAdapter.setCurrentStation(it.id) }

        val playingId = RadioManager.currentStation?.id ?: -1
        val targetIndex = allDrawerStations.indexOfFirst { it.id == playingId }.takeIf { it >= 0 } ?: currentIndex
        val target = targetIndex.coerceIn(0, (allDrawerStations.size - 1).coerceAtLeast(0))
        (rvDrawerStations.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(target, 40)

        rvDrawerStations.postDelayed({
            if (drawerLayout.isDrawerOpen(drawerContent)) {
                focusCurrentStationInDrawer()
            }
        }, 120)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val drawerContent = findViewById<View>(R.id.drawer_content)
        val isDrawerOpen = ::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(drawerContent)

        if (isDrawerOpen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    // Prevent leaving or losing focus inside drawer
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Close drawer if open when pressing RIGHT
                    drawerLayout.closeDrawers()
                    (drawerOpenedFrom ?: btnPlayPause).requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    // Close drawer on BACK
                    drawerLayout.closeDrawers()
                    (drawerOpenedFrom ?: btnPlayPause).requestFocus()
                    return true
                }
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Open drawer when LEFT pressed on back button OR previous button
                val fromBack = btnBack.isFocused
                if ((fromBack || btnPrev.isFocused) && !isDrawerOpen) {
                    drawerOpenedFrom = if (fromBack) btnBack else btnPrev
                    openStationDrawer()
                    return true
                }
                super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Favorite is the last element on the right — stay on it
                if (btnFavorite.isFocused) return true
                super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Transport row (incl. favorite) -> back pill
                if (btnPrev.isFocused || btnPlayPause.isFocused ||
                    btnNext.isFocused || btnFavorite.isFocused
                ) {
                    btnBack.requestFocus()
                    return true
                }
                super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Top buttons -> play/pause
                if (btnBack.isFocused || btnFavorite.isFocused || btnEq.isFocused) {
                    btnPlayPause.requestFocus()
                    return true
                }
                // Transport row: nothing below — stay on the button
                if (btnPrev.isFocused || btnPlayPause.isFocused || btnNext.isFocused) {
                    return true
                }
                super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                // CH+ plays next station
                animateButton(btnNext)
                RadioManager.playNextStation(this)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                // CH- plays previous station
                animateButton(btnPrev)
                RadioManager.playPreviousStation(this)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private val playbackStateListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateLiveIndicator()
            updatePlayPauseIcon(player.isPlaying)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateLiveIndicator()
            updatePlayPauseIcon(isPlaying)
        }
    }

    private fun updateLiveIndicator() {
        if (!::tvLiveLabel.isInitialized) return
        
        val isPlaying = player.isPlaying
        val state = player.playbackState
        val isBuffering = state == androidx.media3.common.Player.STATE_BUFFERING
        
        val colorRes = if (isPlaying && !isBuffering) R.color.live_green else R.color.live_red
        val statusText = if (isPlaying && !isBuffering) getString(R.string.live_stream) else getString(R.string.buffering)
        
        tvLiveLabel.text = statusText
        val color = androidx.core.content.ContextCompat.getColor(this, colorRes)
        tvLiveLabel.setTextColor(color)
        liveDot.background?.setTint(color)
    }





    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let { gestureDetector.onTouchEvent(it) }
        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let { gestureDetector.onTouchEvent(it) }
        return super.dispatchTouchEvent(ev)
    }
    


    // Gesture handling
    override fun onDown(e: MotionEvent): Boolean = true

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Show swipe indicators based on scroll direction
        e1?.let {
            val deltaX = e2.x - it.x
            if (deltaX > 50) {
                showSwipeIndicator(ivSwipeLeft, 0.5f)
            } else if (deltaX < -50) {
                showSwipeIndicator(ivSwipeRight, 0.5f)
            }
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        if (e1 == null) return false
        
        val diffX = e2.x - e1.x
        val diffY = e2.y - e1.y

        if (abs(diffX) > abs(diffY)) {
            // Horizontal swipe
            if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    // Swipe right -> Previous station
                    onSwipePrevious()
                } else {
                    // Swipe left -> Next station
                    onSwipeNext()
                }
                return true
            }
        } else {
            // Vertical swipe
            if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY < 0) {
                    // Swipe up -> Open Equalizer dialog
                    openEqualizerDialog()
                    return true
                }
            }
        }
        return false
    }

    private fun openEqualizerDialog() {
        val dialog = SettingsDialogFragment.newInstance(
            initialTab = SettingsDialogFragment.SettingsTab.EQUALIZER
        )
        dialog.show(supportFragmentManager, SettingsDialogFragment.TAG)
    }

    private fun onSwipeNext() {
        flashSwipeIndicator(ivSwipeRight)
        animateCardSwipe(-1)
        RadioManager.playNextStation(this)
    }

    private fun onSwipePrevious() {
        flashSwipeIndicator(ivSwipeLeft)
        animateCardSwipe(1)
        RadioManager.playPreviousStation(this)
    }

    private fun showSwipeIndicator(view: ImageView, alpha: Float) {
        view.animate()
            .alpha(alpha)
            .setDuration(100)
            .start()
    }

    private fun flashSwipeIndicator(view: ImageView) {
        view.animate()
            .alpha(1f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    private fun animateCardSwipe(direction: Int) {
        val translationX = direction * 50f
        
        cardAlbum.animate()
            .translationX(translationX)
            .alpha(0.5f)
            .setDuration(150)
            .withEndAction {
                cardAlbum.translationX = -translationX
                cardAlbum.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun animateButton(view: View) {
        view.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun animatePlayPause() {
        btnPlayPause.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .rotation(180f)
            .setDuration(150)
            .withEndAction {
                btnPlayPause.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    override fun onStart() {
        super.onStart()

        player.addListener(playbackStateListener)
        player.addListener(tracksListener)
        updateLiveIndicator()
        updateStreamChips()

        RadioManager.addStationChangeListener(stationChangeListener)
        RadioManager.addMetadataListener(metadataListener)
        playCurrent(force = false)
    }

    override fun onStop() {
        super.onStop()
        RadioManager.removeMetadataListener(metadataListener)
        RadioManager.removeStationChangeListener(stationChangeListener)
        player.removeListener(playbackStateListener)
        player.removeListener(tracksListener)
    }

    /**
     * TALAS: stream chips (bitrate + codec) read live from the active
     * ExoPlayer audio track. Hidden when the stream doesn't report them.
     */
    private val tracksListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            updateStreamChips()
        }
    }

    private fun updateStreamChips() {
        var bitrate = -1
        var codecLabel: String? = null

        try {
            for (group in player.currentTracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (i in 0 until group.length) {
                    if (!group.isTrackSelected(i)) continue
                    val f = group.getTrackFormat(i)
                    if (f.bitrate != Format.NO_VALUE) bitrate = f.bitrate
                    codecLabel = codecDisplayName(f)
                    break
                }
                if (codecLabel != null) break
            }
        } catch (_: Exception) {
        }

        if (bitrate > 0) {
            chipBitrate.text = "${bitrate / 1000} kbps"
            chipBitrate.visibility = View.VISIBLE
        } else {
            chipBitrate.visibility = View.GONE
        }

        if (codecLabel != null) {
            chipCodec.text = codecLabel
            chipCodec.visibility = View.VISIBLE
        } else {
            chipCodec.visibility = View.GONE
        }
    }

    private fun codecDisplayName(format: Format): String? {
        val mime = format.sampleMimeType ?: return null
        return when {
            mime == MimeTypes.AUDIO_MPEG -> "MP3"
            mime == MimeTypes.AUDIO_AAC -> "AAC"
            mime == MimeTypes.AUDIO_FLAC -> "FLAC"
            mime == MimeTypes.AUDIO_OGG -> "OGG"
            mime == MimeTypes.AUDIO_OPUS -> "OPUS"
            mime == MimeTypes.AUDIO_VORBIS -> "VORBIS"
            mime == MimeTypes.AUDIO_RAW -> if (format.sampleRate != Format.NO_VALUE) "PCM" else null
            else -> format.codecs?.substringBefore('.')?.uppercase()
        }
    }

    private val metadataListener: (TrackInfo) -> Unit = { track ->
        tvNowPlaying.text = if (track.artist.isNotBlank()) "${track.artist} - ${track.title}" else track.title
        
        val st = stationList.getOrNull(currentIndex)
        val placeholderResId = st?.imageResId?.takeIf { it != 0 } ?: R.drawable.ic_launcher_foreground
        loadLogoWithGlide(track.imageUrl, placeholderResId, placeholderResId)
    }
    


    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    private fun renderStation() {
        val st = stationList.getOrNull(currentIndex) ?: return

        tvStation.text = st.name

        val placeholderResId = st.imageResId.takeIf { it != 0 } ?: R.drawable.ic_launcher_foreground
        updateBlurredBackground(placeholderResId)

        // Show artist image from currentTrackInfo if available, otherwise station logo
        val trackInfo = RadioManager.currentTrackInfo
        loadLogoWithGlide(trackInfo?.imageUrl, placeholderResId, placeholderResId)
        
        tvNowPlaying.text = RadioManager.nowPlayingText ?: getString(R.string.loading)
    }

    private fun playCurrent(force: Boolean) {
        val st = stationList.getOrNull(currentIndex) ?: return
        val same = RadioManager.currentStation?.id == st.id

        if (!hasNotifPermission()) {
            ensureNotificationPermissionIfNeeded()
            return
        }

        if (force || !same || !RadioManager.isPlaying()) {
            RadioManager.playStation(this, st)
        }
        updatePlayPauseIcon(true)
    }

    private fun togglePlayPause() {
        if (player.isPlaying) {
            RadioManager.stop(this)
            updatePlayPauseIcon(false)
        } else {
            playCurrent(force = true)
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        // TALAS: breathing artwork while playing
        cardAlbum.clearAnimation()
        if (isPlaying) {
            cardAlbum.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.breathe))
        }
        updateVisualizerState(isPlaying)
    }

    private var visualizerView: SimpleVisualizerView? = null

    private fun attachVisualizer() {
        if (visualizerContainer.childCount > 0) {
            visualizerContainer.removeAllViews()
        }
        
        // Get theme color from attribute
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(R.attr.themeAccent, typedValue, true)
        val accentColor = typedValue.data
        
        val v = SimpleVisualizerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setColor(accentColor)
            setPlaying(RadioManager.isPlaying())
            setBarCount(28)
            setSymmetrical(true)
        }
        visualizerView = v
        visualizerContainer.addView(v)
    }
    
    // Also need to update playing state in listeners
    private fun updateVisualizerState(isPlaying: Boolean) {
        visualizerView?.setPlaying(isPlaying)
    }

    private fun resolveThemeColor(view: View, attr: Int): Int {
        val tv = android.util.TypedValue()
        view.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    private val icyListener = object : AnalyticsListener {
        override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {
            val st = stationList.getOrNull(currentIndex) ?: return
            if (st.metadataType != MetadataType.STANDARD) return

            for (i in 0 until metadata.length()) {
                when (val entry = metadata.get(i)) {
                    is IcyInfo -> {
                        val title = entry.title?.trim().orEmpty()
                        if (title.isNotEmpty() && title != "Loading...") {
                            applyNowPlaying(title)
                        }
                    }
                    is TextInformationFrame -> {
                        val text = (entry.description ?: entry.value).orEmpty().trim()
                        if (text.isNotEmpty()) {
                            applyNowPlaying(text)
                        }
                    }
                }
            }
        }
    }

    private fun applyNowPlaying(text: String) {
        applyNowPlaying(TrackInfo(text, "", null))
    }

    private fun applyNowPlaying(track: TrackInfo) {
        val display = if (track.artist.isNotBlank()) "${track.artist} - ${track.title}" else track.title
        tvNowPlaying.text = display
        RadioManager.updateNowPlaying(track)

        val st = stationList.getOrNull(currentIndex)
        val stationName = st?.name ?: "Radio"

        // For notification icon: prioritized artist image if available
        // Note: For now keeping station logo for notification to be stable, 
        // but we could load bitmap for artist image too.

        val artworkUri = if (st?.imageResId != null && st.imageResId != 0) {
            Uri.parse("android.resource://$packageName/${st.imageResId}")
        } else null

        val newMeta = MediaMetadata.Builder()
            .setTitle(stationName)
            .setArtist(display)
            .setSubtitle(display)
            // Launcher (Google TV) zahteva DURATION >= 0 u framework metadata.
            .setDurationMs(0L)
            .apply { if (artworkUri != null) setArtworkUri(artworkUri) }
            .build()

        val current = player.currentMediaItem
        if (current != null) {
            val updated = current.buildUpon().setMediaMetadata(newMeta).build()
            player.replaceMediaItem(player.currentMediaItemIndex, updated)
        }
    }

    private fun startMetadataForStation() {
        stopBalkanSocket()
        val st = stationList.getOrNull(currentIndex) ?: return
        val expectedStationId = st.id

        if (st.metadataType == MetadataType.WEBSOCKET_BALKAN) {
            balkanSocket = BalkanWebSocketManager { track ->
                // Run on UI thread and validate station hasn't changed
                runOnUiThread {
                    if (RadioManager.currentStation?.id == expectedStationId) {
                        applyNowPlaying(track)
                    }
                }
            }
            balkanSocket?.start(st.streamUrl)
        }
    }

    private fun stopBalkanSocket() {
        balkanSocket?.close()
        balkanSocket = null
    }
    
    // ============================================================================
    // CLOCK FUNCTIONALITY
    // ============================================================================
    
    @Suppress("DEPRECATION")
    private fun updateClock() {
        if (::tvClock.isInitialized) {
            val currentTime = timeFormat.format(Date())
            tvClock.text = currentTime
        }
        if (::tvClockDate.isInitialized) {
            val cal = Calendar.getInstance()
            val days = arrayOf("ned", "pon", "uto", "sre", "čet", "pet", "sub")
            val months = arrayOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec")
            tvClockDate.text = "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]} · ${cal.get(Calendar.DAY_OF_MONTH)}. ${months[cal.get(Calendar.MONTH)]}"
        }
    }
    
    private fun startClock() {
        clockHandler.post(clockRunnable)
    }
    
    private fun stopClock() {
        clockHandler.removeCallbacks(clockRunnable)
    }
    
    override fun onResume() {
        super.onResume()
        startClock()
    }
    
    override fun onPause() {
        super.onPause()
        stopClock()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Ensure all handler callbacks are removed to prevent memory leaks
        clockHandler.removeCallbacksAndMessages(null)
    }

    private fun updateBlurredBackground(stationResId: Int) {
        val resId = if (stationResId != 0) stationResId else R.drawable.ic_launcher_foreground
        if (resId == currentBackgroundResId) return // Već je postavljen blur za ovu stanicu

        currentBackgroundResId = resId
        val requestOptions = RequestOptions.bitmapTransform(BlurTransformation(25, 3))

        Glide.with(this)
            .load(resId)
            .apply(requestOptions)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(ivBackground)

        ivBackground.alpha = 0.35f
    }

    private fun loadLogoWithGlide(imageUrl: String?, placeholderResId: Int, errorResId: Int) {
        // Pre-validate URL: treat blank strings as null to avoid Glide error spam
        val validUrl = if (imageUrl.isNullOrBlank()) null else imageUrl

        if (validUrl == null) {
            Glide.with(this).clear(ivLogo)
            ivLogo.setImageResource(placeholderResId)
            return
        }

        // Use a RequestListener to log specifically what failed (useful for 404 debugging)
        val errorListener = object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                Log.w("RadioDebug", "Glide failed to load image: $model, error: ${e?.message}")
                return false // allow error placeholder to be set
            }
            override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>, dataSource: DataSource, isFirstResource: Boolean): Boolean = false
        }

        // Standard Glide loading for the main logo - RadioEXGlideModule handles SSL bypass globally
        Glide.with(this)
            .load(validUrl)
            .placeholder(placeholderResId)
            .error(errorResId)
            .listener(errorListener)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(ivLogo)
    }
}
