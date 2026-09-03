package com.gilespii.radioex

import android.app.Application
import com.gilespii.radioex.util.AccessibilityManager
import com.gilespii.radioex.util.FavoritesManager

class RadioEXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AccessibilityManager.initLocale(this)
        FavoritesManager.init(this)

        // Clear poster cache only on app version change to avoid unnecessary regeneration
        val prefs = getSharedPreferences("radioex_app_prefs", MODE_PRIVATE)
        val lastVersion = prefs.getLong("last_poster_version", 0)
        val currentVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) { 0L }
        if (lastVersion != currentVersion) {
            com.gilespii.radioex.tv.TvPosterContentProvider.clearPosterCache(this)
            prefs.edit().putLong("last_poster_version", currentVersion).apply()
        }

        com.gilespii.radioex.tv.TvChannelManager.syncChannels(this)
        com.gilespii.radioex.util.StationLogoCache.preload(this, RadioRepository.getStations())
    }
}
