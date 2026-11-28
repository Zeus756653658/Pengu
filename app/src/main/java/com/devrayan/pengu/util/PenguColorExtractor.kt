package com.devrayan.pengu.util

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pure HSV dominant/vibrant color extractor for Pengu Music Player artwork backdrops.
 */
object PenguColorExtractor {

    suspend fun extractGradientColors(bitmap: Bitmap, fallbackColor: Int): List<Color> =
        withContext(Dispatchers.Default) {
            val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
            val pixels = IntArray(32 * 32)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            if (scaled != bitmap) scaled.recycle()
            extractColorsFromArgbSamples(pixels, fallbackColor).map { Color(it) }
        }

    internal fun extractColorsFromArgbSamples(samples: IntArray, fallbackColor: Int): List<Int> {
        if (samples.isEmpty()) {
            val primary = enhanceColorArgb(fallbackColor, 1.2f)
            return listOf(primary, darkenArgb(primary, 0.55f), 0xFF0A0A10.toInt())
        }

        val bucketCounts = HashMap<Int, Int>(64)
        val bucketRepColor = HashMap<Int, Int>(64)
        for (pixel in samples) {
            if (AndroidColor.alpha(pixel) < 128) continue
            val r = AndroidColor.red(pixel) shr 4
            val g = AndroidColor.green(pixel) shr 4
            val b = AndroidColor.blue(pixel) shr 4
            val key = (r shl 8) or (g shl 4) or b
            bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
            if (!bucketRepColor.containsKey(key)) {
                bucketRepColor[key] = pixel
            }
        }

        val hsv = FloatArray(3)
        var bestColor = fallbackColor
        var bestScore = -1f
        var fallbackDominant = fallbackColor
        var maxPop = -1

        for ((key, pop) in bucketCounts) {
            val rgb = bucketRepColor[key] ?: continue
            AndroidColor.colorToHSV(rgb, hsv)
            val sat = hsv[1]
            val bri = hsv[2]
            if (pop > maxPop) {
                maxPop = pop
                fallbackDominant = rgb
            }
            val vibrancyBonus = if (sat > 0.30f && bri > 0.28f && bri < 0.92f) 1.75f else 0.75f
            val score = pop * vibrancyBonus * ((sat * 1.4f + bri) / 2f + 0.25f)
            if (score > bestScore) {
                bestScore = score
                bestColor = rgb
            }
        }

        AndroidColor.colorToHSV(bestColor, hsv)
        val chosen =
            if (hsv[1] > 0.22f && hsv[2] in 0.18f..0.92f) {
                enhanceColorArgb(bestColor, 1.32f)
            } else {
                enhanceColorArgb(fallbackDominant, 1.15f)
            }

        val secondary = shiftHueAndDarken(chosen, 16f, 0.72f)
        val deep = darkenArgb(chosen, 0.34f)
        return listOf(chosen, secondary, deep, 0xFF09090F.toInt())
    }

    private fun enhanceColorArgb(argb: Int, saturationFactor: Float): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        hsv[1] = (hsv[1] * saturationFactor).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.88f).coerceIn(0.32f, 0.82f)
        return AndroidColor.HSVToColor(hsv)
    }

    private fun darkenArgb(argb: Int, factor: Float): Int {
        val r = (AndroidColor.red(argb) * factor).toInt().coerceIn(0, 255)
        val g = (AndroidColor.green(argb) * factor).toInt().coerceIn(0, 255)
        val b = (AndroidColor.blue(argb) * factor).toInt().coerceIn(0, 255)
        return AndroidColor.rgb(r, g, b)
    }

    private fun shiftHueAndDarken(argb: Int, hueDelta: Float, valFactor: Float): Int {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        hsv[0] = (hsv[0] + hueDelta) % 360f
        hsv[2] = (hsv[2] * valFactor).coerceIn(0.18f, 0.72f)
        return AndroidColor.HSVToColor(hsv)
    }
}
