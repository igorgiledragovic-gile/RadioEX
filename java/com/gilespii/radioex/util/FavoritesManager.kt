package com.gilespii.radioex.util

import android.content.Context
import android.content.SharedPreferences

object FavoritesManager {
    private const val PREF_NAME = "radio_favorites"
    private const val KEY_FAVORITES = "favorite_ids"

    private lateinit var prefs: SharedPreferences
    private val favoriteIds = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        favoriteIds.addAll(saved)
    }

    fun isFavorite(stationId: Int): Boolean {
        return favoriteIds.contains(stationId.toString())
    }

    fun addFavorite(stationId: Int) {
        favoriteIds.add(stationId.toString())
        save()
    }

    fun removeFavorite(stationId: Int) {
        favoriteIds.remove(stationId.toString())
        save()
    }

    fun toggleFavorite(stationId: Int): Boolean {
        return if (isFavorite(stationId)) {
            removeFavorite(stationId)
            false
        } else {
            addFavorite(stationId)
            true
        }
    }

    private fun save() {
        prefs.edit().putStringSet(KEY_FAVORITES, favoriteIds).apply()
    }

    fun getFavoriteCount(): Int {
        return favoriteIds.size
    }

    fun getFavoriteIds(): Set<Int> {
        return favoriteIds.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun getFavoritesJson(): String {
        return getFavoritesFullJson()
    }

    fun getFavoritesFullJson(): String {
        val root = org.json.JSONObject()
        root.put("app", "RadioEX")
        root.put("version", 1)
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        root.put("exportDate", dateFormat.format(java.util.Date()))

        val allStations = com.gilespii.radioex.RadioRepository.getStations()
        val favStations = allStations.filter { isFavorite(it.id) }
        root.put("count", favStations.size)

        val jsonArray = org.json.JSONArray()
        favStations.forEach { st ->
            val obj = org.json.JSONObject()
            obj.put("id", st.id)
            obj.put("name", st.name)
            obj.put("streamUrl", st.streamUrl)
            obj.put("metadataUrl", st.metadataUrl)
            obj.put("metadataType", st.metadataType.name)
            jsonArray.put(obj)
        }
        root.put("favorites", jsonArray)

        return root.toString(2)
    }

    enum class ImportMode {
        MERGE,
        REPLACE
    }

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val totalFavorites: Int,
        val errorMessage: String? = null
    )

    fun importFavoritesJson(json: String): Boolean {
        return importFavoritesFromJson(json, ImportMode.REPLACE).success
    }

    fun importFavoritesFromJson(jsonString: String, mode: ImportMode): ImportResult {
        return try {
            val trimmed = jsonString.trim()
            val extractedIds = mutableSetOf<String>()
            val allStations = com.gilespii.radioex.RadioRepository.getStations()
            val allStationIds = allStations.map { it.id }.toSet()

            if (trimmed.startsWith("{")) {
                val root = org.json.JSONObject(trimmed)
                if (root.has("favorites")) {
                    val array = root.getJSONArray("favorites")
                    for (i in 0 until array.length()) {
                        val item = array.get(i)
                        when (item) {
                            is org.json.JSONObject -> {
                                val id = item.optInt("id", -1)
                                if (id in allStationIds) {
                                    extractedIds.add(id.toString())
                                } else {
                                    // Fallback match by streamUrl or name
                                    val streamUrl = item.optString("streamUrl")
                                    val name = item.optString("name")
                                    val match = allStations.firstOrNull { 
                                        (streamUrl.isNotEmpty() && it.streamUrl == streamUrl) ||
                                        (name.isNotEmpty() && it.name.equals(name, ignoreCase = true))
                                    }
                                    if (match != null) extractedIds.add(match.id.toString())
                                }
                            }
                            is Number -> {
                                val id = item.toInt()
                                if (id in allStationIds) extractedIds.add(id.toString())
                            }
                            is String -> {
                                val id = item.toIntOrNull()
                                if (id != null && id in allStationIds) extractedIds.add(id.toString())
                            }
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                val array = org.json.JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    when (item) {
                        is org.json.JSONObject -> {
                            val id = item.optInt("id", -1)
                            if (id in allStationIds) extractedIds.add(id.toString())
                        }
                        is Number -> {
                            val id = item.toInt()
                            if (id in allStationIds) extractedIds.add(id.toString())
                        }
                        is String -> {
                            val id = item.toIntOrNull()
                            if (id != null && id in allStationIds) extractedIds.add(id.toString())
                        }
                    }
                }
            } else {
                return ImportResult(false, 0, favoriteIds.size, "Fajl ne sadrži validan JSON format.")
            }

            if (extractedIds.isEmpty()) {
                return ImportResult(false, 0, favoriteIds.size, "U fajlu nije pronađena nijedna važeća radio stanica.")
            }

            if (mode == ImportMode.REPLACE) {
                favoriteIds.clear()
            }
            favoriteIds.addAll(extractedIds)
            save()

            ImportResult(true, extractedIds.size, favoriteIds.size)
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(false, 0, favoriteIds.size, "Greška pri čitanju fajla: ${e.message}")
        }
    }
}

