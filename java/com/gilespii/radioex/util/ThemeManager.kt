package com.gilespii.radioex.util

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREFS_NAME = "radioex_theme_prefs"
    private const val OLD_PREFS_NAME = "radiowinamp_theme_prefs"
    private const val KEY_THEME = "selected_theme"
    
    fun init(context: Context) {
        // Follow the palette of the selected theme (TALAS has light + dark themes)
        val mode = if (Constants.isLightTheme(getCurrentThemeId(context))) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
    
    fun getCurrentThemeId(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_THEME, Constants.THEMES[0].id) ?: Constants.THEMES[0].id
    }
    
    fun getCurrentThemeResId(context: Context): Int {
        val themeId = getCurrentThemeId(context)
        return Constants.THEMES.find { it.id == themeId }?.resId ?: Constants.THEMES[0].resId
    }
    
    fun setTheme(context: Context, themeId: String) {
        getPrefs(context).edit().putString(KEY_THEME, themeId).apply()
    }
    
    fun applyTheme(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) {
            init(activity)
            activity.recreate()
        }
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_THEME)) {
            val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
            if (oldPrefs.contains(KEY_THEME)) {
                oldPrefs.getString(KEY_THEME, null)?.let {
                    prefs.edit().putString(KEY_THEME, it).apply()
                }
            }
        }
        return prefs
    }
}
