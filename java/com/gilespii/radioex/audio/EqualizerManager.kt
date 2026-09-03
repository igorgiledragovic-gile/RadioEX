package com.gilespii.radioex.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log

/**
 * SMART EQUALIZER with Studio-Grade Balanced Presets
 * 
 * Rešava problem utišavanja zvuka (volume dip) prilikom promene fabričkih preseta:
 * 1. Preseti se primenjuju identičnim mehanizmom kao ručni slajderi (čisti `setBandLevel` pozivi).
 * 2. Nema instanciranja ili rekonfiguracije dinamičkih kompresora (LoudnessEnhancer) koji bi izazivali
 *    AudioFlinger effect ramp-down ili audio ducking na TV hardveru.
 * 3. Vrednosti nivoa se automatski ograničavaju (clamp) na hardverski podržan opseg (`getBandLevelRange()`).
 * 4. Rezultat: Prelazak između bilo kojih preseta ili slajdera je 100% trenutan i besprekoran, bez pada glasnoće.
 */
data class EqualizerPreset(
    val name: String,
    val bandLevels: ShortArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EqualizerPreset
        return name == other.name && bandLevels.contentEquals(other.bandLevels)
    }
    override fun hashCode(): Int = 31 * name.hashCode() + bandLevels.contentHashCode()
}

object EqualizerManager {
    private const val PREFS_NAME = "radiowinamp_eq_prefs_v4"
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_PRESET = "eq_preset"
    private const val KEY_BAND_PREFIX = "eq_band_"

    @Volatile
    private var equalizer: Equalizer? = null
    @Volatile
    private var isEnabled = false
    @Volatile
    private var currentPresetIndex = 0
    @Volatile
    private var currentAudioSessionId: Int = 0
    @Volatile
    private var isInitializing = false

    // Namenski single-thread executor za audio efekte (sprečava ANR i zamrzavanja na TV-u)
    private val audioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * Studijski balansirani Zero-Ceiling preseti (Peak = 0 dBFS):
     * Vršni nivo svakog profila je tačno 0 dB (ili niže).
     * Nema pozitivnog pojačanja iznad digitalnog maksimuma, što sprečava AOSP / NXP
     * headroom atenuaciju (-3dB do -6dB) i eliminiše pad jačine zvuka na TV-u.
     */
    val presets = listOf(
        // Flat: Neutralan, čist izvorni zvuk emitovanja
        EqualizerPreset("Flat", shortArrayOf(0, 0, 0, 0, 0)),
        
        // Pop: Topli vokali i umeren bas (vokalni opseg na 0 dB, bas i visoki suptilno povučeni)
        EqualizerPreset("Pop", shortArrayOf(-200, -100, 0, -50, -200)),
        
        // Rock: Klasična V kriva sa čvrstim basom i jasnim činelama (bas i visoki na 0 dB)
        EqualizerPreset("Rock", shortArrayOf(0, -50, -200, -50, 0)),
        
        // Vocal: Izuzetna razumljivost govora i pevanja (vokali na 0 dB)
        EqualizerPreset("Vocal", shortArrayOf(-250, -100, 0, -50, -200)),
        
        // Bass Boost: Pun, dubok bas (niske frekvencije na 0 dB, ostalo atenuirano)
        EqualizerPreset("Bass Boost", shortArrayOf(0, -100, -250, -200, -200)),
        
        // Jazz: Topao akustični balans sa prijatnim kontrabasom
        EqualizerPreset("Jazz", shortArrayOf(0, -50, -100, -50, 0)),
        
        // Classical: Širok orkestarski prostor i prirodna transparentnost
        EqualizerPreset("Classical", shortArrayOf(0, -50, -100, -50, 0)),
        
        // Dance / Electronic: Snažan klupski udarac i definisani visoki
        EqualizerPreset("Dance", shortArrayOf(0, -100, -250, -100, -50)),
        
        // Hip Hop: Dubok 808 bas i artikulisan ritam
        EqualizerPreset("Hip Hop", shortArrayOf(0, -150, -250, -200, -200))
    )

    // Korisnički definisani nivoi (za prikaz u UI i perzistenciju)
    private val userBandLevels = ShortArray(10) { 0 }

    fun init(context: Context, audioSessionId: Int) {
        if (audioSessionId <= 0) {
            Log.d("EqualizerManager", "init() skipped - invalid audioSessionId: $audioSessionId")
            return
        }
        
        Log.d("EqualizerManager", "init() sessionId: $audioSessionId")
        
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            Log.d("EqualizerManager", "Already initialized for session $audioSessionId, skipping")
            return
        }
        
        currentAudioSessionId = audioSessionId
        isEnabled = loadEnabled(context)
        currentPresetIndex = loadPresetIndex(context)

        // Učitaj nivoe iz aktivnog preseta
        val activeLevels = presets.getOrNull(currentPresetIndex)?.bandLevels
        if (activeLevels != null) {
            for (i in 0 until minOf(userBandLevels.size, activeLevels.size)) {
                userBandLevels[i] = activeLevels[i]
            }
        } else {
            for (i in userBandLevels.indices) {
                userBandLevels[i] = loadBandLevel(context, i).toShort()
            }
        }

        audioExecutor.execute {
            try {
                try {
                    equalizer?.enabled = false
                    equalizer?.release()
                } catch (e: Exception) {
                    Log.e("EqualizerManager", "Error releasing previous equalizer", e)
                }

                val eq = Equalizer(0, audioSessionId)
                equalizer = eq
                
                // Primeni nivoe preko Smart Zero-Ceiling normalizera
                applyHardwareLevels()

                eq.enabled = isEnabled

                val numBands = eq.numberOfBands.toInt()
                val (minRange, maxRange) = getBandLevelRange()
                Log.d("EqualizerDiag", """
                    EQ init complete:
                    sessionId=$audioSessionId
                    eqInstance=${System.identityHashCode(eq)}
                    enabled=$isEnabled
                    preset=${presets.getOrNull(currentPresetIndex)?.name}
                    numberOfBands=$numBands
                    bandRange=($minRange, $maxRange)
                """.trimIndent())
            } catch (e: Exception) {
                Log.e("EqualizerManager", "Failed to initialize audio equalizer", e)
                equalizer = null
                isEnabled = false
            }
        }
    }

    fun isEnabled(): Boolean = isEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        Log.d("EqualizerManager", "setEnabled: $enabled, current preset: ${presets.getOrNull(currentPresetIndex)?.name}")
        isEnabled = enabled
        saveEnabled(context, enabled)
        
        audioExecutor.execute {
            try {
                if (enabled) {
                    applyHardwareLevels()
                    equalizer?.enabled = true
                    Log.d("EqualizerManager", "EQ enabled successfully")
                } else {
                    equalizer?.enabled = false
                    Log.d("EqualizerManager", "EQ disabled successfully")
                }
            } catch (e: Exception) {
                Log.e("EqualizerManager", "Failed to set EQ enabled state to $enabled", e)
            }
        }
    }

    fun applyPreset(context: Context, presetIndex: Int) {
        if (presetIndex !in presets.indices) {
            Log.w("EqualizerManager", "Invalid preset index: $presetIndex")
            return
        }
        
        val preset = presets[presetIndex]
        currentPresetIndex = presetIndex
        savePresetIndex(context, presetIndex)

        val count = minOf(userBandLevels.size, preset.bandLevels.size)
        for (i in 0 until count) {
            userBandLevels[i] = preset.bandLevels[i]
            saveBandLevel(context, i, preset.bandLevels[i].toInt())
        }

        audioExecutor.execute {
            applyHardwareLevels()
            Log.d("EqualizerDiag", "Preset applied: ${preset.name}, user levels: [${preset.bandLevels.joinToString()}]")
        }
    }

    /**
     * Smart Zero-Ceiling Normalizer:
     * Izračunava vršni nivo: peak = max(0, max_k(userBandLevels[k])).
     * Ako korisnik podigne bilo koji pojas iznad 0 dB (npr. +3 dB), svi pojasevi se u pozadini
     * transliraju na dole tako da hardverski maksimum nikada ne prelazi 0 dBFS.
     * Na ovaj način NXP LifeVibes LVM limiter nikada ne obara celokupnu jačinu signala,
     * a relativni tonalni balans koji je korisnik podesio ostaje 100% veran.
     */
    private fun applyHardwareLevels() {
        val eq = equalizer ?: return
        try {
            val (minRange, maxRange) = getBandLevelRange()
            val numBands = eq.numberOfBands.toInt()
            val count = minOf(numBands, userBandLevels.size)

            var peak = 0
            for (i in 0 until count) {
                if (userBandLevels[i] > peak) {
                    peak = userBandLevels[i].toInt()
                }
            }

            for (i in 0 until count) {
                val normalizedLevel = (userBandLevels[i] - peak)
                    .coerceIn(minRange.toInt(), maxRange.toInt())
                    .toShort()
                try {
                    eq.setBandLevel(i.toShort(), normalizedLevel)
                } catch (e: Exception) {
                    Log.e("EqualizerManager", "Failed to set hardware band $i level to $normalizedLevel", e)
                }
            }
            Log.d("EqualizerDiag", "Hardware levels applied (peak offset = $peak mB): [${(0 until count).map { (userBandLevels[it] - peak) }.joinToString()}]")
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error in applyHardwareLevels", e)
        }
    }

    fun getNumberOfBands(): Short {
        return try {
            equalizer?.numberOfBands ?: 5
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Safe fallback getNumberOfBands: 5", e)
            5
        }
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        return try {
            val range = equalizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
            Pair(range[0], range[1])
        } catch (e: Exception) {
            Log.w("EqualizerManager", "Safe fallback getBandLevelRange: -1500..1500", e)
            Pair((-1500).toShort(), 1500.toShort())
        }
    }

    fun getBandLevel(band: Short): Short {
        val bandIdx = band.toInt()
        if (bandIdx in userBandLevels.indices) {
            return userBandLevels[bandIdx]
        }
        val preset = presets.getOrNull(currentPresetIndex)
        if (preset != null && bandIdx < preset.bandLevels.size) {
            return preset.bandLevels[bandIdx]
        }
        return 0
    }

    fun setBandLevel(context: Context, band: Short, level: Short) {
        val bandIdx = band.toInt()
        if (bandIdx in userBandLevels.indices) {
            userBandLevels[bandIdx] = level
            saveBandLevel(context, bandIdx, level.toInt())
        }
        audioExecutor.execute {
            applyHardwareLevels()
        }
    }

    fun getCenterFreq(band: Short): Int {
        return try {
            equalizer?.getCenterFreq(band) ?: getDefaultCenterFreq(band)
        } catch (e: Exception) {
            getDefaultCenterFreq(band)
        }
    }

    private fun getDefaultCenterFreq(band: Short): Int {
        return when (band.toInt()) {
            0 -> 60000
            1 -> 230000
            2 -> 910000
            3 -> 3600000
            4 -> 14000000
            else -> 1000000
        }
    }

    fun getFrequencyLabel(band: Short): String {
        val freq = getCenterFreq(band)
        return when {
            freq >= 1000000 -> "${freq / 1000000}kHz"
            freq >= 1000 -> "${freq / 1000}Hz"
            else -> "${freq}Hz"
        }
    }

    fun getCurrentPresetIndex(): Int = currentPresetIndex
    
    fun getCurrentSessionId(): Int = currentAudioSessionId

    fun release() {
        audioExecutor.execute {
            releaseInternal()
        }
    }

    private fun releaseInternal() {
        try {
            equalizer?.enabled = false
            equalizer?.release()
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing equalizer", e)
        } finally {
            equalizer = null
            currentAudioSessionId = 0
            isInitializing = false
        }
    }

    private fun saveBandLevel(context: Context, band: Int, level: Int) {
        getPrefs(context).edit().putInt("$KEY_BAND_PREFIX$band", level).apply()
    }

    private fun loadBandLevel(context: Context, band: Int): Int {
        return getPrefs(context).getInt("$KEY_BAND_PREFIX$band", 0)
    }

    // Preferences
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun saveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private fun loadEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    private fun savePresetIndex(context: Context, index: Int) {
        getPrefs(context).edit().putInt(KEY_PRESET, index).apply()
    }

    private fun loadPresetIndex(context: Context): Int {
        return getPrefs(context).getInt(KEY_PRESET, 0)
    }
}
