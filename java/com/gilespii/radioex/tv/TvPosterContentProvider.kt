package com.gilespii.radioex.tv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.gilespii.radioex.RadioRepository
import com.gilespii.radioex.RadioStation
import java.io.File
import java.io.FileNotFoundException

/**
 * Public ContentProvider that serves cached TV Preview Program station posters
 * over standard `content://` URI to Google TV LauncherX / System UI.
 */
class TvPosterContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.gilespii.radioex.tvposters"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        private const val TAG = "TvPosterProvider"

        /**
         * Clears all cached poster images so updated visuals are immediately regenerated.
         */
        fun clearPosterCache(context: Context) {
            try {
                val dir = File(context.filesDir, "tv_posters")
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.delete() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear poster cache: ${e.message}")
            }
        }

        /**
         * Generates and returns a 16:9 banner Bitmap (640x360) featuring:
         * - Stretched, heavily blurred ambient background derived from the station's logo
         * - Darkened translucent overlay & soft vignette for contrast and depth
         * - Crisp, centered station logo with subtle depth shadow
         */
        fun generateCardBitmap(context: Context, station: RadioStation): Bitmap {
            val width = 640
            val height = 360
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val logo = try {
                BitmapFactory.decodeResource(context.resources, station.imageResId)
            } catch (e: Exception) {
                null
            }

            if (logo != null) {
                // 1. Generate blurred background from logo
                try {
                    // Downsample to small resolution for ultra-fast and intense blur
                    val downW = 80
                    val downH = 45
                    val downscaled = Bitmap.createScaledBitmap(logo, downW, downH, true)
                    val blurred = applyFastBoxBlur(downscaled, radius = 5, passes = 2)

                    // Draw blurred bitmap stretched to 16:9 canvas
                    val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    val bgDst = Rect(0, 0, width, height)
                    canvas.drawBitmap(blurred, null, bgDst, filterPaint)

                    downscaled.recycle()
                    blurred.recycle()
                } catch (e: Exception) {
                    Log.w(TAG, "Blur fallback: ${e.message}")
                    val fallbackPaint = Paint().apply { color = Color.parseColor("#151820") }
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallbackPaint)
                }

                // 2. Dark translucent overlay to reduce background transparency / opacity (moody contrast)
                val overlayPaint = Paint().apply {
                    color = Color.argb(115, 8, 10, 16) // ~45% dark tint for rich vibrant background
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

                // 3. Subtle radial vignette / edge shadow
                val vignetteShader = RadialGradient(
                    width / 2f, height / 2f, width * 0.65f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(95, 0, 0, 0)),
                    null,
                    Shader.TileMode.CLAMP
                )
                val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = vignetteShader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)

                // 4. Large Crisp Logo spanning full top-to-bottom height (360px)
                val targetHeight = height.toFloat() // 360px full height
                val scale = targetHeight / logo.height
                val scaledW = (logo.width * scale).toInt()
                val scaledH = height
                val left = (width - scaledW) / 2
                val top = 0
                val dstRect = Rect(left, top, left + scaledW, height)

                // Soft side shadow behind logo edges for subtle depth over blurred background
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 0, 0, 0)
                }
                val shadowRect = RectF(
                    (left - 8).toFloat(),
                    0f,
                    (left + scaledW + 8).toFloat(),
                    height.toFloat()
                )
                canvas.drawRect(shadowRect, shadowPaint)

                // Crisp logo spanning full top-to-bottom height
                canvas.drawBitmap(logo, null, dstRect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

                logo.recycle()
            } else {
                // Fallback dark gradient background
                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(Color.parseColor("#151820"), Color.parseColor("#202532")),
                    null,
                    Shader.TileMode.CLAMP
                )
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            return bitmap
        }

        /**
         * Fast pure-Kotlin 2-pass box blur on small bitmaps.
         */
        private fun applyFastBoxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
            val w = src.width
            val h = src.height
            val inPix = IntArray(w * h)
            val outPix = IntArray(w * h)
            src.getPixels(inPix, 0, w, 0, 0, w, h)

            for (p in 0 until passes) {
                // Horizontal pass
                for (y in 0 until h) {
                    val rowOffset = y * w
                    for (x in 0 until w) {
                        var a = 0; var r = 0; var g = 0; var b = 0
                        var count = 0
                        for (kx in -radius..radius) {
                            val px = (x + kx).coerceIn(0, w - 1)
                            val c = inPix[rowOffset + px]
                            a += (c ushr 24) and 0xFF
                            r += (c ushr 16) and 0xFF
                            g += (c ushr 8) and 0xFF
                            b += c and 0xFF
                            count++
                        }
                        outPix[rowOffset + x] = ((a / count) shl 24) or
                                ((r / count) shl 16) or
                                ((g / count) shl 8) or
                                (b / count)
                    }
                }

                // Vertical pass
                for (x in 0 until w) {
                    for (y in 0 until h) {
                        var a = 0; var r = 0; var g = 0; var b = 0
                        var count = 0
                        for (ky in -radius..radius) {
                            val py = (y + ky).coerceIn(0, h - 1)
                            val c = outPix[py * w + x]
                            a += (c ushr 24) and 0xFF
                            r += (c ushr 16) and 0xFF
                            g += (c ushr 8) and 0xFF
                            b += c and 0xFF
                            count++
                        }
                        inPix[y * w + x] = ((a / count) shl 24) or
                                ((r / count) shl 16) or
                                ((g / count) shl 8) or
                                (b / count)
                    }
                }
            }

            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            out.setPixels(inPix, 0, w, 0, 0, w, h)
            return out
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: throw FileNotFoundException("Context is null")
        val fileName = uri.lastPathSegment ?: throw FileNotFoundException("Missing file name in URI: $uri")
        val posterDir = File(ctx.filesDir, "tv_posters").apply { if (!exists()) mkdirs() }
        val file = File(posterDir, fileName)

        if (!file.exists() || file.length() == 0L) {
            val idStr = fileName.substringBeforeLast('.').removePrefix("station_").substringBefore('_')
            val stationId = idStr.toIntOrNull()
            if (stationId != null) {
                val station = RadioRepository.getStationById(stationId)
                if (station != null) {
                    try {
                        val bitmap = generateCardBitmap(ctx, station)
                        file.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed on-demand poster generation for $fileName: ${e.message}")
                    }
                }
            }
        }

        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Poster file does not exist: ${file.absolutePath}")
            throw FileNotFoundException("File not found: $fileName")
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val pfd = openFile(uri, mode) ?: return null
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
