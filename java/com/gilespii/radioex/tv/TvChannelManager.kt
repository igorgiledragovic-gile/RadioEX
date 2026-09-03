package com.gilespii.radioex.tv

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.gilespii.radioex.HistoryRepository
import com.gilespii.radioex.MainActivity
import com.gilespii.radioex.PlayerActivity
import com.gilespii.radioex.R
import com.gilespii.radioex.RadioRepository
import com.gilespii.radioex.RadioStation
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TV Channel Manager for Android TV / Google TV Home Screen
 * Publishes and updates the "RadioEX: Nastavi slušanje" channel and "Play Next" programs.
 */
object TvChannelManager {
    private const val TAG = "TvChannelManager"
    private const val CHANNEL_INTERNAL_ID = "radioex_continue_channel"
    private const val CHANNEL_DISPLAY_NAME = "RadioEX: Nastavi slušanje"
    private const val CHANNEL_DESCRIPTION = "Brzi pristup poslednje slušanim stanicama"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getPosterUri(context: Context, station: RadioStation): Uri {
        return try {
            val dir = File(context.filesDir, "tv_posters").apply { if (!exists()) mkdirs() }
            val file = File(dir, "station_${station.id}_v2.png")
            if (!file.exists() || file.length() == 0L) {
                val bitmap = TvPosterContentProvider.generateCardBitmap(context, station)
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            Uri.parse("${TvPosterContentProvider.CONTENT_URI}/station_${station.id}_v2.png")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache poster for station ${station.id}: ${e.message}")
            Uri.parse("${TvPosterContentProvider.CONTENT_URI}/station_${station.id}_v2.png")
        }
    }

    /**
     * Synchronizes the channel and its programs asynchronously.
     */
    fun syncChannels(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                TvPosterContentProvider.clearPosterCache(appContext)
                val channelId = getOrCreateChannel(appContext) ?: return@launch
                updateProgramsForChannel(appContext, channelId)
            } catch (e: Exception) {
                Log.w(TAG, "Error syncing TV channels: ${e.message}")
            }
        }
    }

    /**
     * Gets existing channel ID or creates a new one, ensuring appLinkIntentUri and browsable state are set.
     */
    private fun getOrCreateChannel(context: Context): Long? {
        val helper = PreviewChannelHelper(context)
        return try {
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appLinkUri = Uri.parse(mainIntent.toUri(Intent.URI_INTENT_SCHEME))

            val logo = try {
                BitmapFactory.decodeResource(context.resources, R.drawable.tv_banner_radioex)
            } catch (_: Exception) {
                null
            }

            val existing = helper.allChannels.find { it.internalProviderId == CHANNEL_INTERNAL_ID }
            if (existing != null) {
                val builder = PreviewChannel.Builder(existing)
                    .setDisplayName(CHANNEL_DISPLAY_NAME)
                    .setDescription(CHANNEL_DESCRIPTION)
                    .setAppLinkIntentUri(appLinkUri)

                if (logo != null) {
                    builder.setLogo(logo)
                }

                helper.updatePreviewChannel(existing.id, builder.build())
                TvContractCompat.requestChannelBrowsable(context, existing.id)
                existing.id
            } else {
                val builder = PreviewChannel.Builder()
                    .setDisplayName(CHANNEL_DISPLAY_NAME)
                    .setDescription(CHANNEL_DESCRIPTION)
                    .setAppLinkIntentUri(appLinkUri)
                    .setInternalProviderId(CHANNEL_INTERNAL_ID)

                if (logo != null) {
                    builder.setLogo(logo)
                }

                val channelId = helper.publishDefaultChannel(builder.build())
                Log.d(TAG, "Created default TV channel: $channelId")
                channelId
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get or create TV channel: ${e.message}")
            null
        }
    }

    /**
     * Updates preview programs on the channel based on history or popular stations.
     */
    private fun updateProgramsForChannel(context: Context, channelId: Long) {
        val helper = PreviewChannelHelper(context)
        try {
            // Resolve stations to display (history first, filled with popular defaults up to 8)
            val historyItems = HistoryRepository.getHistory(context)
            val historyStations = historyItems.mapNotNull { item ->
                RadioRepository.getStationById(item.stationId)
            }.distinctBy { it.id }

            val defaultStations = RadioRepository.getPopularDefaultStations()
            val stations = (historyStations + defaultStations).distinctBy { it.id }.take(8)

            // Remove previous programs for this channel
            try {
                val programsUri = TvContractCompat.buildPreviewProgramsUriForChannel(channelId)
                context.contentResolver.delete(programsUri, null, null)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to clear old programs: ${e.message}")
            }

            // Publish updated programs in order
            for ((index, station) in stations.withIndex()) {
                val playIntent = createPlayIntent(context, station, index)
                val intentUri = Uri.parse(playIntent.toUri(Intent.URI_INTENT_SCHEME))
                val posterUri = getPosterUri(context, station)

                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setTitle(station.name)
                    .setDescription("Uživo prenos · Kliknite za puštanje")
                    .setPosterArtUri(posterUri)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_TRACK)
                    .setWeight(100 - index)
                    .setIntentUri(intentUri)
                    .setInternalProviderId("station_${station.id}")
                    .build()

                helper.publishPreviewProgram(program)
            }

            Log.d(TAG, "Successfully published ${stations.size} programs to TV channel $channelId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update programs: ${e.message}")
        }
    }

    /**
     * Updates the system "Watch Next" / "Play Next" row with the currently playing station.
     */
    fun updateWatchNext(context: Context, station: RadioStation) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val helper = PreviewChannelHelper(appContext)
                val playIntent = createPlayIntent(appContext, station, 0)
                val intentUri = Uri.parse(playIntent.toUri(Intent.URI_INTENT_SCHEME))
                val posterUri = getPosterUri(appContext, station)

                val program = WatchNextProgram.Builder()
                    .setTitle(station.name)
                    .setDescription("Poslednje slušano · RadioEX")
                    .setPosterArtUri(posterUri)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_TRACK)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setIntentUri(intentUri)
                    .setInternalProviderId("station_${station.id}")
                    .build()

                helper.publishWatchNextProgram(program)
                Log.d(TAG, "Published WatchNext program for station ${station.name}")
            } catch (e: Exception) {
                Log.d(TAG, "WatchNext update skipped: ${e.message}")
            }
        }
    }

    private fun createPlayIntent(context: Context, station: RadioStation, index: Int): Intent {
        return Intent(context, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("radioex://play?stationId=${station.id}")
            putExtra("station_id", station.id)
            putExtra(PlayerActivity.EXTRA_STATION_INDEX, index)
            putExtra("auto_play", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
