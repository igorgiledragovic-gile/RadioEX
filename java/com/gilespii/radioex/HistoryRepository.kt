package com.gilespii.radioex

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class ListeningHistoryItem(
    val stationId: Int,
    val stationName: String,
    val stationImageResId: Int,
    val songTitle: String?,
    val timestamp: Long
)

object HistoryRepository {
    private const val PREFS_NAME = "radiowinamp_history"
    private const val KEY_HISTORY = "listening_history"
    private const val MAX_HISTORY_SIZE = 100

    private val gson = Gson()
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun addEntry(context: Context, stationId: Int, stationName: String, imageResId: Int, songTitle: String?) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                val history = getHistory(appContext).toMutableList()
                
                // Uvek ukloni prethodni unos za istu stanicu da nema duplikata i da stanica ode na vrh
                history.removeAll { it.stationId == stationId }
                
                // Dodaj na početak liste
                history.add(0, ListeningHistoryItem(
                    stationId = stationId,
                    stationName = stationName,
                    stationImageResId = imageResId,
                    songTitle = songTitle,
                    timestamp = System.currentTimeMillis()
                ))
                
                // Trim to max size
                val trimmed = history.take(MAX_HISTORY_SIZE)
                
                saveHistory(appContext, trimmed)
            } catch (e: Exception) {
                // Ignore background persistence errors
            }
        }
    }

    fun getHistory(context: Context): List<ListeningHistoryItem> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        
        return try {
            val type = object : TypeToken<List<ListeningHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearHistory(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            getPrefs(appContext).edit().remove(KEY_HISTORY).apply()
        }
    }

    private fun saveHistory(context: Context, history: List<ListeningHistoryItem>) {
        val json = gson.toJson(history)
        getPrefs(context).edit().putString(KEY_HISTORY, json).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
