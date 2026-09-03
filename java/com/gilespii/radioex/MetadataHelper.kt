package com.gilespii.radioex

import android.util.Log
import android.util.LruCache
import com.gilespii.radioex.BuildConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * MetadataHelper - Profesionalni metadata parser za balkanske radio stanice
 *
 * Podržava: Naxi, Radio S, TDI, Shoutcast, Icecast, ICY in-stream
 *
 * @version 2.0.0
 */
object MetadataHelper {

    // ═══════════════════════════════════════════════════════════
    //  KONSTANTE
    // ═══════════════════════════════════════════════════════════
    private const val TAG = "RadioDebug"

    // Cache
    private const val CACHE_SIZE = 50
    private const val CACHE_DURATION_MS = 15_000L

    // Network
    private const val TIMEOUT_CONNECT_MS = 8L
    private const val TIMEOUT_READ_MS = 12L

    // ICY protokol
    private const val ICY_METADATA_MULTIPLIER = 16
    private const val BUFFER_SIZE = 4096

    // Connection pool
    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_DURATION_SECONDS = 30L

    // API Key - loaded from BuildConfig (local.properties)
    private val apiKey: String = BuildConfig.NAXI_FIREBASE_API_KEY

    // Naxi station mapping - sortirano po dužini (duži pattern prvi)
    private val naxiStationMap: List<Pair<String, String>> by lazy {
        listOf(
            "exyurock" to "exyurock",
            "chillwave" to "chillwave",
            "millennium" to "millennium",
            "evergreen" to "evergreen",
            "clubbing" to "clubbing",
            "boomerang" to "boomerang",
            "fitness" to "fitness",
            "classic" to "classic",
            "latino" to "latino",
            "lounge" to "lounge",
            "blues" to "blues-rock",
            "fresh" to "fresh",
            "adore" to "adore",
            "dance" to "dance",
            "house" to "house",
            "disco" to "disco",
            "reggae" to "reggae",
            "funk" to "funk",
            "chill" to "chill",
            "cafe" to "cafe",
            "jazz" to "jazz",
            "love" to "love",
            "gold" to "gold",
            "rock" to "rock",
            "boem" to "boem",
            "play" to "play",
            "exyu" to "exyu",
            "80s" to "80e",
            "80e" to "80e",
            "90s" to "90e",
            "90e" to "90e",
            "70s" to "70e",
            "70e" to "70e",
            "rnb" to "rnb",
            "mix" to "mix"
        ).sortedByDescending { it.first.length }
    }

    // Thread-safe LRU cache
    private val cache = LruCache<String, Pair<TrackInfo, Long>>(CACHE_SIZE)

    // Optimizovani OkHttp klijent
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONNECT_MS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_READ_MS, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(
                MAX_IDLE_CONNECTIONS,
                KEEP_ALIVE_DURATION_SECONDS,
                TimeUnit.SECONDS
            ))
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) RadioWinamp/1.0")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    // Insecure client for Radio S (SSL certificate issues)
    private val insecureClient: OkHttpClient by lazy {
        try {
            // Create a trust manager that does not validate certificate chains
            val trustAllCerts = arrayOf<javax.net.ssl.X509TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
            
            // Install the all-trusting trust manager
            val sslContext = javax.net.ssl.SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            
            // Create an ssl socket factory with our all-trusting manager
            val sslSocketFactory = sslContext.socketFactory
            
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_CONNECT_MS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_READ_MS, TimeUnit.SECONDS)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0])
                .hostnameVerifier { _, _ -> true }
                .retryOnConnectionFailure(true)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) RadioWinamp/1.0")
                        .header("Accept", "*/*")
                        .build()
                    chain.proceed(request)
                }
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create insecure client, falling back to regular", e)
            client
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  JAVNE FUNKCIJE - SINHRONA VERZIJA
    // ═══════════════════════════════════════════════════════════

    /**
     * Dohvata metadata za datu radio stanicu (sinhrono)
     * UPOZORENJE: Ne pozivaj sa Main thread-a!
     */
    fun getMetadata(
        url: String,
        type: MetadataType,
        forceRefresh: Boolean = false
    ): TrackInfo {
        Log.d(TAG, "┌─ Fetching metadata")
        Log.d(TAG, "│  URL: $url")
        Log.d(TAG, "│  Type: $type")

        // Proveri cache
        if (!forceRefresh) {
            getCachedMetadata(url)?.let { cached ->
                Log.d(TAG, "└─ Cache HIT ✓")
                return cached
            }
        }

        val result = try {
            when (type) {
                MetadataType.AUTO -> getSmartMetadata(url)
                MetadataType.STANDARD -> getStandardMetadata(url) ?: TrackInfo.empty()
                MetadataType.JSON_NAXI -> getNaxiMetadata(url)
                MetadataType.JSON_RADIOS -> getRadioSMetadata(url)
                MetadataType.SHOUTCAST_TEXT -> getShoutcastV1Metadata(url) ?: TrackInfo.empty()
                MetadataType.WEBSOCKET_BALKAN -> TrackInfo.empty()
            }
        } catch (e: Exception) {
            Log.e(TAG, "│  Error: ${e.message}")
            Log.e(TAG, "└─ Failed ✗")
            TrackInfo.empty()
        }

        Log.d(TAG, "│  Result: ${result.artist} - ${result.title}")
        Log.d(TAG, "└─ Success ✓")

        cacheMetadata(url, result)
        return result
    }

    // ═══════════════════════════════════════════════════════════
    //  CACHE OPERACIJE
    // ═══════════════════════════════════════════════════════════

    private fun getCachedMetadata(url: String): TrackInfo? {
        // LruCache is already thread-safe, no need for synchronized block
        val entry = cache.get(url) ?: return null
        val (info, timestamp) = entry

        return if (System.currentTimeMillis() - timestamp < CACHE_DURATION_MS) {
            info
        } else {
            cache.remove(url)
            null
        }
    }

    private fun cacheMetadata(url: String, info: TrackInfo) {
        // LruCache is already thread-safe, no need for synchronized block
        cache.put(url, info to System.currentTimeMillis())
    }

    // ═══════════════════════════════════════════════════════════
    //  SMART MODE
    // ═══════════════════════════════════════════════════════════

    private fun getSmartMetadata(streamUrl: String): TrackInfo {
        val domain = streamUrl.extractDomain()

        return when {
            domain.containsAny("naxi", "playradio") || streamUrl.containsAny("naxi", "play") ->
                getNaxiMetadata(extractNaxiStationId(streamUrl))

            domain.containsAny("radios.rs", "streaming.radios", "maksnet.tv") ->
                getRadioSMetadata(streamUrl)

            domain.containsAny("radio-tri", "radiotri") ->
                getShoutcastV1Metadata(streamUrl) ?: TrackInfo.empty()

            domain.contains("tdiradio") || streamUrl.contains("listen.radionomy") ->
                getTDIRadioMetadata(streamUrl)

            domain.containsAny("rsg.rs", "dontstopmusic") ->
                getStandardMetadata(streamUrl) ?: TrackInfo.empty()

            else -> getFallbackMetadata(streamUrl)
        }
    }

    private fun getFallbackMetadata(streamUrl: String): TrackInfo {
        Log.d(TAG, "│  Trying fallback methods...")

        getFromCurrentsongTxt(streamUrl)?.let { return it }
        getIcecastMetadata(streamUrl)?.let { return it }
        getStandardMetadata(streamUrl)?.let { return it }
        getShoutcastV1Metadata(streamUrl)?.let { return it }

        return TrackInfo("Live Radio", "", null)
    }

    // ═══════════════════════════════════════════════════════════
    //  1. NAXI RADIO (Firebase Firestore)
    // ═══════════════════════════════════════════════════════════

    private fun getNaxiMetadata(stationId: String): TrackInfo {
        val url = buildString {
            append("https://firestore.googleapis.com/v1/projects/naxiproject/")
            append("databases/(default)/documents/now_playing/")
            append(stationId)
            append("?key=")
            append(apiKey)
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.naxi.rs/")
                .header("Origin", "https://www.naxi.rs")
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        response.body?.string()?.let { parseNaxiResponse(it, stationId) }
                            ?: TrackInfo.fallback("Naxi Radio", stationId)
                    }
                    else -> {
                        Log.w(TAG, "│  Naxi HTTP ${response.code}")
                        TrackInfo.fallback("Naxi Radio", stationId)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "│  Naxi timeout")
            TrackInfo.fallback("Naxi Radio", stationId)
        } catch (e: Exception) {
            Log.e(TAG, "│  Naxi error: ${e.message}")
            TrackInfo.fallback("Naxi Radio", stationId)
        }
    }

    private fun parseNaxiResponse(response: String, stationId: String): TrackInfo {
        return try {
            val root = JSONObject(response)
            if (!root.has("fields")) return TrackInfo.fallback("Naxi Radio", stationId)

            val fields = root.getJSONObject("fields")
            if (!fields.has("now_playing_json")) return TrackInfo.fallback("Naxi Radio", stationId)

            val innerJson = fields.getJSONObject("now_playing_json").getString("stringValue")
            val data = JSONObject(innerJson)
            if (!data.has("now_playing")) return TrackInfo.fallback("Naxi Radio", stationId)

            val nowPlaying = data.getJSONObject("now_playing")

            val title = nowPlaying.optString("title", "").trim()
            val artist = nowPlaying.optString("artist", "").trim()
            var imageUrl = nowPlaying.optString("artistImg", "").trim()

            if (imageUrl.isNotEmpty() && !imageUrl.startsWith("http")) {
                val path = if (imageUrl.startsWith("/")) imageUrl else "/$imageUrl"
                imageUrl = "https://www.naxi.rs$path"
            }

            TrackInfo(title, artist, imageUrl.takeIf { it.isNotBlank() })

        } catch (e: Exception) {
            Log.e(TAG, "│  Naxi parse error: ${e.message}")
            TrackInfo.fallback("Naxi Radio", stationId)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  2. RADIO S (JSON API)
    // ═══════════════════════════════════════════════════════════

    private fun getRadioSMetadata(apiUrl: String): TrackInfo {
        return try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Referer", "https://www.radios.rs/")
                .build()

            insecureClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "│  Radio S HTTP ${response.code}")
                    return TrackInfo.fallback("Radio S")
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) return TrackInfo.fallback("Radio S")

                val json = JSONObject(body)

                val title = json.optString("song", "")
                    .ifEmpty { json.optString("title", "") }
                    .trim()

                val artist = json.optString("artist", "").trim()

                val image = json.optString("newimage", "")
                    .ifEmpty { json.optString("cover", "") }
                    .trim()

                TrackInfo(title, artist, image.takeIf { it.isNotEmpty() })
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "│  Radio S timeout")
            TrackInfo.fallback("Radio S")
        } catch (e: Exception) {
            Log.e(TAG, "│  Radio S error: ${e.message}")
            TrackInfo.fallback("Radio S")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  3. STANDARD ICY METADATA (In-Stream)
    // ═══════════════════════════════════════════════════════════

    private fun getStandardMetadata(streamUrl: String): TrackInfo? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null

        return try {
            connection = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Icy-MetaData", "1")
                setRequestProperty("User-Agent", "RadioEX/1.0")
                setRequestProperty("Connection", "close")
                connectTimeout = (TIMEOUT_CONNECT_MS * 1000).toInt()
                readTimeout = (TIMEOUT_READ_MS * 1000).toInt()
                instanceFollowRedirects = true
            }

            connection.connect()

            val metaInt = connection.getHeaderField("icy-metaint")?.toIntOrNull()
            if (metaInt == null || metaInt <= 0) return null

            inputStream = connection.inputStream
            skipBytes(inputStream, metaInt.toLong())

            val lengthByte = inputStream.read()
            if (lengthByte <= 0) return null

            val metaLength = lengthByte * ICY_METADATA_MULTIPLIER
            val metaBuffer = ByteArray(metaLength)
            var totalRead = 0

            while (totalRead < metaLength) {
                val read = inputStream.read(metaBuffer, totalRead, metaLength - totalRead)
                if (read == -1) break
                totalRead += read
            }

            String(metaBuffer, 0, totalRead, StandardCharsets.UTF_8).parseIcyMetadata()

        } catch (e: SocketTimeoutException) {
            null
        } catch (e: Exception) {
            Log.d(TAG, "│  ICY error: ${e.message}")
            null
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { connection?.disconnect() } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  4. SHOUTCAST HTML / RADIO TRI
    // ═══════════════════════════════════════════════════════════

    private fun getShoutcastV1Metadata(url: String): TrackInfo? {
        val urlsToTry = listOf(
            url,
            "${url.trimEnd('/')}/",
            "${url.substringBeforeLast("/")}/7.html"
        ).distinct()

        for (tryUrl in urlsToTry) {
            try {
                val request = Request.Builder().url(tryUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.parseShoutcastHtml()?.let { return it }
                    }
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun String.parseShoutcastHtml(): TrackInfo? {
        // Radio TRI - regex
        val triRegex = """class=["']noapesma["'][^>]*>(.*?)</div>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        triRegex.find(this)?.groupValues?.getOrNull(1)?.let { rawData ->
            val songTitle = android.text.Html.fromHtml(rawData, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (songTitle.isNotEmpty()) return songTitle.parseArtistTitle()
        }

        // Standard Shoutcast
        if (contains("Current Song", ignoreCase = true)) {
            val rawData = substringAfter("Current Song")
                .substringAfter("<td").substringAfter(">").substringBefore("</td>")
            val songTitle = android.text.Html.fromHtml(rawData, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().trim()
            if (songTitle.isNotEmpty()) return songTitle.parseArtistTitle()
        }

        // Shoutcast 7.html
        if (contains("<body>") && count { it == ',' } >= 6) {
            val content = substringAfter("<body>").substringBefore("</body>")
            val parts = content.split(",")
            if (parts.size >= 7) {
                parts.getOrNull(6)?.trim()?.let {
                    if (it.isNotEmpty()) return it.parseArtistTitle()
                }
            }
        }

        // JSON format
        if (trimStart().startsWith("{")) {
            try {
                val json = JSONObject(this)
                val title = json.optString("songtitle", "").ifEmpty { json.optString("title", "") }
                if (title.isNotEmpty()) return title.parseArtistTitle()
            } catch (_: Exception) {}
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  5. ICECAST (status-json.xsl)
    // ═══════════════════════════════════════════════════════════

    private fun getIcecastMetadata(streamUrl: String): TrackInfo? {
        val baseUrl = streamUrl.substringBeforeLast("/")
        val endpoints = listOf("$baseUrl/status-json.xsl", "$baseUrl/json.xsl")

        for (endpoint in endpoints) {
            try {
                val text = fetchText(endpoint) ?: continue
                val json = JSONObject(text)
                val icestats = json.optJSONObject("icestats") ?: continue
                val source = icestats.optJSONObject("source")
                    ?: icestats.optJSONArray("source")?.optJSONObject(0)
                    ?: continue

                val title = source.optString("title", "").trim()
                if (title.isNotEmpty()) return title.parseArtistTitle()
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  6. CURRENTSONG.TXT
    // ═══════════════════════════════════════════════════════════

    private fun getFromCurrentsongTxt(streamUrl: String): TrackInfo? {
        val baseUrl = streamUrl.substringBeforeLast("/")
        val endpoints = listOf("$baseUrl/currentsong.txt", "$baseUrl/currentsong", "$baseUrl/nowplaying.txt")

        for (endpoint in endpoints) {
            try {
                val text = fetchText(endpoint) ?: continue
                if (text.isBlank() || text.contains("404") || text.contains("<html", ignoreCase = true)) continue
                if (text.length > 500) continue
                return text.trim().parseArtistTitle()
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════
    //  7. TDI RADIO (Radionomy API)
    // ═══════════════════════════════════════════════════════════

    private fun getTDIRadioMetadata(streamUrl: String): TrackInfo {
        val uid = streamUrl.extractParam("radiouid")
        if (uid.isNullOrEmpty()) return TrackInfo.fallback("TDI Radio")

        val url = "https://api.radionomy.com/currentsong.cfm?radiouid=$uid&type=xml"

        return try {
            val xml = fetchText(url) ?: return TrackInfo.fallback("TDI Radio")
            val title = xml.extractXmlTag("title")
            val artist = xml.extractXmlTag("artist")
            val cover = xml.extractXmlTag("cover")

            if (title.isEmpty() && artist.isEmpty()) TrackInfo.fallback("TDI Radio")
            else TrackInfo(title, artist, cover.takeIf { it.isNotEmpty() })
        } catch (e: Exception) {
            TrackInfo.fallback("TDI Radio")
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPER FUNKCIJE
    // ═══════════════════════════════════════════════════════════

    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun skipBytes(inputStream: InputStream, numBytes: Long) {
        var remaining = numBytes
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            remaining -= read
        }
    }

    private fun extractNaxiStationId(url: String): String {
        val lowercaseUrl = url.lowercase()
        return naxiStationMap.firstOrNull { (key, _) -> lowercaseUrl.contains(key) }?.second ?: "naxi"
    }

    // ═══════════════════════════════════════════════════════════
    //  EXTENSION FUNKCIJE
    // ═══════════════════════════════════════════════════════════

    @JvmName("parseArtistTitleStatic")
    fun parseArtistTitle(text: String): TrackInfo = text.parseArtistTitle()

    private fun String.parseArtistTitle(): TrackInfo {
        val clean = trim().replace("\u0000", "").replace("  ", " ")
        if (clean.isEmpty()) return TrackInfo.empty()

        val separators = listOf(" - ", " – ", " — ", " | ", " / ")
        for (separator in separators) {
            if (clean.contains(separator)) {
                val parts = clean.split(separator, limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    return TrackInfo(parts[1].trim(), parts[0].trim(), null)
                }
            }
        }
        return TrackInfo(clean, "", null)
    }

    private fun String.parseIcyMetadata(): TrackInfo? {
        if (!contains("StreamTitle='")) return null
        val startIndex = indexOf("StreamTitle='") + 13
        val endIndex = indexOf("';", startIndex)
        if (endIndex <= startIndex) return null
        val rawTitle = substring(startIndex, endIndex).trim().replace("\u0000", "")
        return if (rawTitle.isEmpty()) null else rawTitle.parseArtistTitle()
    }

    private fun String.extractDomain(): String = try { URL(this).host.lowercase() } catch (_: Exception) { "" }

    private fun String.extractParam(param: String): String? {
        val marker = "$param="
        if (!contains(marker)) return null
        return substringAfter(marker).substringBefore("&").substringBefore(" ").trim()
            .takeIf { it.isNotEmpty() && it != this }
    }

    private fun String.extractXmlTag(tag: String): String {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        return if (contains(startTag) && contains(endTag)) {
            substringAfter(startTag).substringBefore(endTag).trim()
        } else ""
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        val lower = this.lowercase()
        return keywords.any { lower.contains(it.lowercase()) }
    }
}