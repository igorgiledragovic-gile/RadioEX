package com.gilespii.radioex.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.gilespii.radioex.MainActivity
import com.gilespii.radioex.PlayerActivity
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioManager
import com.gilespii.radioex.RadioStation
import com.gilespii.radioex.TrackInfo
import com.gilespii.radioex.util.FavoritesManager
import com.bumptech.glide.Glide

import com.gilespii.radioex.util.StationLogoCache

class MiniPlayerFragment : Fragment() {

    private lateinit var ivLogo: ImageView
    private lateinit var tvStation: TextView
    private lateinit var tvArtist: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var kbpsChip: LinearLayout
    private lateinit var tvKbps: TextView
    private lateinit var visualizer: SimpleVisualizerView

    private var isShowing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.mini_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivLogo = view.findViewById(R.id.iv_mini_logo)
        tvStation = view.findViewById(R.id.tv_mini_station)
        tvArtist = view.findViewById(R.id.tv_mini_artist)
        btnPlay = view.findViewById(R.id.btn_mini_play)
        btnPrev = view.findViewById(R.id.btn_mini_prev)
        btnNext = view.findViewById(R.id.btn_mini_next)
        btnFavorite = view.findViewById(R.id.btn_mini_favorite)
        btnFullscreen = view.findViewById(R.id.btn_mini_fullscreen)
        kbpsChip = view.findViewById(R.id.kbps_chip)
        tvKbps = view.findViewById(R.id.tv_kbps)
        visualizer = view.findViewById(R.id.mini_visualizer)
        visualizer.setBarCount(18)
        visualizer.setSymmetrical(true)

        tvStation.isSelected = true
        tvArtist.isSelected = true

        btnPlay.setOnClickListener { togglePlayPause() }
        btnPrev.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            RadioManager.playPreviousStation(ctx)
        }
        btnNext.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            RadioManager.playNextStation(ctx)
        }
        btnFavorite.setOnClickListener {
            val station = RadioManager.currentStation ?: return@setOnClickListener
            FavoritesManager.toggleFavorite(station.id)
            updateFavoriteIcon()
        }
        btnFullscreen.setOnClickListener { openPlayer() }

        // Setup unified focus scaling for mini player controls
        val density = resources.displayMetrics.density
        val focusInterpolator = android.view.animation.DecelerateInterpolator(1.5f)
        listOf(btnPrev, btnPlay, btnNext, btnFavorite, btnFullscreen).forEach { btn ->
            btn.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150)
                    .setInterpolator(focusInterpolator)
                    .start()
                v.translationZ = if (hasFocus) 6f * density else 0f
            }
        }

        view.findViewById<View>(R.id.card_logo)?.setOnClickListener { openPlayer() }
        tvStation.setOnClickListener { openPlayer() }

        updateUI()
    }

    fun requestPlayFocus() {
        view?.post {
            btnPlay.requestFocus()
        }
    }

    fun requestFullscreenFocus() {
        view?.post {
            btnFullscreen.requestFocus()
        }
    }

    override fun onStart() {
        super.onStart()
        RadioManager.addStationChangeListener(stationChangeListener)
        RadioManager.addMetadataListener(metadataListener)
        val player = RadioManager.player
        if (player != null) {
            player.addListener(playbackStateListener)
            isPlayerListenerAdded = true
        }
        updateUI()
    }

    override fun onStop() {
        super.onStop()
        RadioManager.removeMetadataListener(metadataListener)
        RadioManager.removeStationChangeListener(stationChangeListener)
        if (isPlayerListenerAdded) {
            RadioManager.player?.removeListener(playbackStateListener)
            isPlayerListenerAdded = false
        }
    }

    private var isPlayerListenerAdded = false

    private val metadataListener: (TrackInfo) -> Unit = { track ->
        updateArtist(track)
    }

    private val playbackStateListener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayPauseIcon()
        }
    }

    private fun updateUI() {
        val station = RadioManager.currentStation
        if (station != null) {
            updateStationUI(station)
        }
        updatePlayPauseIcon()
        updateFavoriteIcon()
        updateArtist()
    }

    private val stationChangeListener: (RadioStation, Int) -> Unit = { station, _ ->
        updateStationUI(station)
        updateFavoriteIcon()
        updateArtist()
    }

    private fun updateStationUI(station: RadioStation) {
        tvStation.text = station.name
        val ctx = context
        if (station.imageResId != 0 && ctx != null) {
            val bmp = StationLogoCache.getBitmap(ctx, station.imageResId)
            if (bmp != null) {
                ivLogo.setImageBitmap(bmp)
            } else {
                ivLogo.setImageResource(station.imageResId)
            }
        } else {
            ivLogo.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    private fun updateArtist(track: TrackInfo? = RadioManager.currentTrackInfo) {
        val text = when {
            track == null -> ""
            track.artist.isNotBlank() && track.title.isNotBlank() -> "${track.artist} — ${track.title}"
            track.title.isNotBlank() -> track.title
            track.artist.isNotBlank() -> track.artist
            else -> ""
        }
        if (text.isNotBlank()) {
            tvArtist.text = text
            tvArtist.visibility = View.VISIBLE
        } else {
            tvArtist.visibility = View.GONE
        }
    }

    private fun updateFavoriteIcon() {
        val station = RadioManager.currentStation ?: return
        val isFav = FavoritesManager.isFavorite(station.id)
        if (isFav) {
            btnFavorite.setImageResource(R.drawable.ic_heart)
            btnFavorite.setColorFilter(getThemeColor(R.attr.themeAccent))
        } else {
            btnFavorite.setImageResource(R.drawable.ic_heart)
            btnFavorite.setColorFilter(getThemeColor(R.attr.themeTextMuted))
        }
    }

    private fun togglePlayPause() {
        val ctx = context ?: return
        if (RadioManager.isPlaying()) {
            RadioManager.stop(ctx)
        } else {
            val station = RadioManager.currentStation
            if (station != null) {
                RadioManager.playStation(ctx, station)
            }
        }
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = RadioManager.isPlaying()
        val icon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlay.setImageResource(icon)
        visualizer.setPlaying(isPlaying)
    }

    private fun openPlayer() {
        val station = RadioManager.currentStation ?: return
        val index = RadioManager.stationList.indexOfFirst { it.id == station.id }
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STATION_INDEX, index.coerceAtLeast(0))
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        activity?.overridePendingTransition(R.anim.slide_up, R.anim.fade_out)
    }

    fun show() {
        if (isShowing) return
        isShowing = true
        view?.visibility = View.VISIBLE
        view?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(300)?.start()
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        view?.animate()?.alpha(0f)?.translationY(view?.height?.toFloat() ?: 100f)?.setDuration(200)?.withEndAction {
            view?.visibility = View.GONE
        }?.start()
    }

    fun refresh() {
        updateUI()
        val hasStation = RadioManager.currentStation != null
        if (hasStation && !isShowing) {
            show()
        } else if (!hasStation && isShowing) {
            hide()
        }
    }

    private fun getThemeColor(attrId: Int): Int {
        val context = context ?: return 0
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }
}
