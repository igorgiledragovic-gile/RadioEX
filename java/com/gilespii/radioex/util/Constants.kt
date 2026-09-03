package com.gilespii.radioex.util

import com.gilespii.radioex.Category
import com.gilespii.radioex.R

/**
 * App Constants
 * Contains all static configuration data for categories and themes
 */
object Constants {

    /**
     * Radio station categories for filtering
     */
    val CATEGORIES = listOf(
        Category("all", "Sve", R.drawable.ic_rail_home),
        Category("favorites", "Favoriti", R.drawable.ic_heart),
        Category("hitfm", "TDI", R.drawable.ic_signal),
        Category("naxi", "Naxi", R.drawable.ic_signal),
        Category("radios", "S Radio", R.drawable.ic_signal),
        Category("scg", "SCG", R.drawable.ic_signal),
        Category("play", "Play", R.drawable.ic_signal),
        Category("ostalo", "Ostalo", R.drawable.ic_signal)
    )

    fun getCategories(context: android.content.Context): List<Category> {
        return listOf(
            Category("all", context.getString(R.string.category_all), R.drawable.ic_rail_home),
            Category("favorites", context.getString(R.string.category_favorites), R.drawable.ic_heart),
            Category("hitfm", "TDI", R.drawable.ic_signal),
            Category("naxi", "Naxi", R.drawable.ic_signal),
            Category("radios", "S Radio", R.drawable.ic_signal),
            Category("scg", "SCG", R.drawable.ic_signal),
            Category("play", "Play", R.drawable.ic_signal),
            Category("ostalo", context.getString(R.string.category_other), R.drawable.ic_signal)
        )
    }

    /**
     * Theme configuration data class
     */
    data class ThemeConfig(
        val id: String,
        val name: String,
        val resId: Int
    )

    /**
     * Available UI themes — TALAS design set (20 themes)
     * Ported from novi_dizajn/src/data/themes.ts
     */
    val THEMES = listOf(
        ThemeConfig("midnightglass", "Midnight Glass", R.style.Theme_RadioWinamp_MidnightGlass),
        ThemeConfig("neonwave", "Neon Wave", R.style.Theme_RadioWinamp_NeonWave),
        ThemeConfig("aurora", "Aurora Gradient", R.style.Theme_RadioWinamp_Aurora),
        ThemeConfig("retroanalog", "Retro Analog", R.style.Theme_RadioWinamp_RetroAnalog),
        ThemeConfig("minimalmono", "Minimal Mono", R.style.Theme_RadioWinamp_MinimalMono),
        ThemeConfig("sunsetbento", "Sunset Bento", R.style.Theme_RadioWinamp_SunsetBento),
        ThemeConfig("cybergrid", "Cyber Grid", R.style.Theme_RadioWinamp_CyberGrid),
        ThemeConfig("nordicfrost", "Nordic Frost", R.style.Theme_RadioWinamp_NordicFrost),
        ThemeConfig("deepspace", "Deep Space", R.style.Theme_RadioWinamp_DeepSpace),
        ThemeConfig("vinylnoir", "Vinyl Noir", R.style.Theme_RadioWinamp_VinylNoir),
        ThemeConfig("pasteldream", "Pastel Dream", R.style.Theme_RadioWinamp_PastelDream),
        ThemeConfig("carbonfiber", "Carbon Fiber", R.style.Theme_RadioWinamp_CarbonFiber),
        ThemeConfig("oceandepth", "Ocean Depth", R.style.Theme_RadioWinamp_OceanDepth),
        ThemeConfig("solarflare", "Solar Flare", R.style.Theme_RadioWinamp_SolarFlare),
        ThemeConfig("forestmist", "Forest Mist", R.style.Theme_RadioWinamp_ForestMist),
        ThemeConfig("urbanconcrete", "Urban Concrete", R.style.Theme_RadioWinamp_UrbanConcrete),
        ThemeConfig("royalvelvet", "Royal Velvet", R.style.Theme_RadioWinamp_RoyalVelvet),
        ThemeConfig("icecrystal", "Ice Crystal", R.style.Theme_RadioWinamp_IceCrystal),
        ThemeConfig("terracotta", "Terracotta Warm", R.style.Theme_RadioWinamp_Terracotta),
        ThemeConfig("matrixpulse", "Matrix Pulse", R.style.Theme_RadioWinamp_MatrixPulse)
    )

    /** Themes that use a light base palette */
    val LIGHT_THEMES = setOf("nordicfrost", "pasteldream", "urbanconcrete", "icecrystal", "terracotta")

    fun isLightTheme(themeId: String): Boolean = themeId in LIGHT_THEMES
}
