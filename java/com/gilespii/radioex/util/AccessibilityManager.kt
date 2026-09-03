package com.gilespii.radioex.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.concurrent.CopyOnWriteArrayList

object AccessibilityManager {
    private const val PREFS_NAME = "radioex_a11y_prefs"
    private const val KEY_REDUCED_MOTION = "reduced_motion"
    private const val KEY_LANGUAGE = "selected_language"

    const val LANG_SR = "sr"
    const val LANG_EN = "en"

    private val motionListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isReducedMotion(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REDUCED_MOTION, false)
    }

    fun setReducedMotion(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply()
        for (listener in motionListeners) {
            listener.invoke(enabled)
        }
    }

    fun addReducedMotionListener(listener: (Boolean) -> Unit) {
        motionListeners.add(listener)
    }

    fun removeReducedMotionListener(listener: (Boolean) -> Unit) {
        motionListeners.remove(listener)
    }

    fun getLanguage(context: Context): String {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_LANGUAGE)) {
            prefs.edit().putString(KEY_LANGUAGE, LANG_SR).apply()
            return LANG_SR
        }
        return prefs.getString(KEY_LANGUAGE, LANG_SR) ?: LANG_SR
    }

    fun setLanguage(context: Context, langCode: String) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, langCode).apply()
        val appLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun wrapContext(context: Context): Context {
        val lang = getLanguage(context)
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun applyLocale(context: Context) {
        val lang = getLanguage(context)
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val resources = context.resources
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    fun initLocale(context: Context) {
        applyLocale(context)
        val currentLang = getLanguage(context)
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (currentAppLocales.isEmpty || currentAppLocales.toLanguageTags() != currentLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(currentLang))
        }
    }
}
