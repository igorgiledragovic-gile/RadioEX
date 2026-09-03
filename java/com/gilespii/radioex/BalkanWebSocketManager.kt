package com.gilespii.radioex

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.net.URI
import java.util.concurrent.TimeUnit

// Modeli podataka koji odgovaraju JSON strukturi sa sajta
data class BalkanResponse(val streams: List<BalkanStream>?)
data class BalkanStream(val path: String, val song: String?, val imageUrl: String?)

/**
 * Optimized WebSocket manager with shared OkHttpClient instance.
 * Reusing the client improves performance and reduces memory usage.
 * FIXED: Thread-safe webSocket access with synchronization.
 * FIXED: Exponential backoff reconnection on failure.
 */
class BalkanWebSocketManager(
    private val onTrackUpdate: (TrackInfo) -> Unit
) {
    // Singleton OkHttpClient instance - shared across all connections
    companion object {
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
        
        // Reuse Gson instance (thread-safe)
        private val sharedGson = Gson()

        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val MAX_RETRY_DELAY_MS = 30000L
    }
    
    private var webSocket: WebSocket? = null
    // Lock for thread-safe webSocket access
    private val webSocketLock = Object()

    // Čuvamo "path" stanice (npr. "/live/narodna.mp3") da znamo šta da tražimo
    private var currentStationPath: String = ""
    private var currentStreamUrl: String = ""

    @Volatile
    private var isClosed = false
    private var retryCount = 0
    private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun start(stationUrl: String) {
        isClosed = false
        retryCount = 0
        currentStreamUrl = stationUrl
        closeInternal() // Zatvori prethodni ako postoji

        // Izvuci path iz stream URL-a
        try {
            val uri = URI(stationUrl)
            // Server šalje "/live/narodna.mp3", a stream URL je možda duži
            currentStationPath = uri.path ?: ""
            // Mali fix ako URL sadrži /radio prefix koji server ne šalje u JSON-u
            if (currentStationPath.contains("/radio/live/")) {
                currentStationPath = currentStationPath.replace("/radio", "")
            }
        } catch (e: Exception) {
            currentStationPath = stationUrl
        }

        connect()
    }

    private fun connect() {
        if (isClosed) return

        val request = Request.Builder()
            .url("wss://radiobalkan.live/wsl6") // Adresa izvučena iz JS-a
            .build()

        synchronized(webSocketLock) {
            webSocket = sharedClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("BalkanWS", "Konektovan na WebSocket")
                retryCount = 0 // Reset retry counter on successful connection
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val response = sharedGson.fromJson(text, BalkanResponse::class.java)
                    if (response.streams != null) {
                        // Tražimo stream koji se poklapa sa našom stanicom
                        val match = response.streams.find {
                            // Proveravamo da li se path poklapa
                            currentStationPath.endsWith(it.path)
                        }

                        if (match != null) {
                            // --- LOGOVANJE ---
                            if (match.imageUrl.isNullOrEmpty()) {
                                Log.d("RadioDebug", "BALKAN WS: Nema slike u JSON-u za pesmu: ${match.song}")
                            } else {
                                Log.d("RadioDebug", "BALKAN WS: Stigla slika! URL: ${match.imageUrl}")
                            }

                            // Čišćenje teksta (logika iz njihovog JS fajla)
                            var rawTitle = match.song ?: ""
                            rawTitle = rawTitle.split("---")[0]
                            rawTitle = rawTitle.split("- SINGLE")[0].trim()

                            // --- NOVA LOGIKA: Razdvajanje Artista i Naslova ---
                            var finalArtist = ""
                            var finalTitle = rawTitle

                            if (rawTitle.contains(" - ")) {
                                val parts = rawTitle.split(" - ", limit = 2)
                                finalArtist = parts[0].trim()
                                finalTitle = parts[1].trim()
                            }

                            // Sada šaljemo 3 parametra: Title, Artist, ImageUrl
                            val info = TrackInfo(
                                title = finalTitle,
                                artist = finalArtist,
                                imageUrl = match.imageUrl
                            )
                            onTrackUpdate(info)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BalkanWS", "Greška u parsiranju: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("BalkanWS", "Greška konekcije: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("BalkanWS", "WebSocket zatvoren: $code $reason")
                if (!isClosed) {
                    scheduleReconnect()
                }
            }
        })
        }
    }

    private fun scheduleReconnect() {
        if (isClosed || retryCount >= MAX_RETRIES) {
            Log.w("BalkanWS", "Reconnect exhausted ($retryCount/$MAX_RETRIES) or closed")
            return
        }

        val delay = (INITIAL_RETRY_DELAY_MS * (1L shl retryCount.coerceAtMost(4)))
            .coerceAtMost(MAX_RETRY_DELAY_MS)
        retryCount++
        Log.d("BalkanWS", "Reconnect attempt $retryCount/$MAX_RETRIES in ${delay}ms")

        retryHandler.postDelayed({
            if (!isClosed) {
                closeInternal()
                connect()
            }
        }, delay)
    }

    fun close() {
        isClosed = true
        retryHandler.removeCallbacksAndMessages(null)
        closeInternal()
    }
    
    private fun closeInternal() {
        synchronized(webSocketLock) {
            webSocket?.close(1000, "Promena stanice")
            webSocket = null
        }
    }
}