package com.example.core.camera

import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Effect
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter

enum class FilterCategory(val label: String) {
    ALL("All"),
    PORTRAIT("Portrait & Natural"),
    VINTAGE("Vintage & Film"),
    WARM_GOLD("Warm & Golden"),
    MONO_MOODY("B&W & Moody"),
    TONE_SHARP("Tone & Sharp"),
    LIFESTYLE("Lifestyle & Beauty"),
    TRENDING("Trending")
}

data class VideoFilterPreset(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val description: String,
    val swatchStart: Long,
    val swatchEnd: Long,
    val brightness: Float = 0f,      // -0.5..0.5
    val contrast: Float = 1f,        // 0.5..1.6
    val saturation: Float = 1f,      // 0..2
    val warmth: Float = 0f,          // -0.4..0.4
    val sharpness: Float = 0f,       // 0..1
    val tintArgb: Long = 0x00000000L,
    val vignetteAlpha: Float = 0f,
    val softGlow: Float = 0f,
    val isGrayscale: Boolean = false,
    val isSepia: Boolean = false,
    val isTrending: Boolean = false,
    val sizeKb: Int = 180
)

object VideoFilterCatalog {

    val allFilters: List<VideoFilterPreset> = listOf(
        VideoFilterPreset("original", "Original", FilterCategory.PORTRAIT, "Unfiltered crystal camera feed", 0xFF334155, 0xFF64748B),
        VideoFilterPreset("natural", "Natural", FilterCategory.PORTRAIT, "Clean balanced tones & subtle clarity", 0xFF38BDF8, 0xFF34D399, brightness = 0.06f, contrast = 1.06f, saturation = 1.06f, warmth = 0.03f),
        VideoFilterPreset("portrait", "Portrait", FilterCategory.PORTRAIT, "Flattering skin luminance & soft depth", 0xFFFDA4AF, 0xFFFBBF24, brightness = 0.10f, contrast = 1.05f, saturation = 1.04f, warmth = 0.06f, softGlow = 0.18f, vignetteAlpha = 0.08f),

        VideoFilterPreset("vintage", "Vintage", FilterCategory.VINTAGE, "Faded analog warmth & soft blacks", 0xFFD97706, 0xFF78350F, brightness = 0.04f, contrast = 0.92f, saturation = 0.82f, warmth = 0.16f, tintArgb = 0x16B45309, vignetteAlpha = 0.20f),
        VideoFilterPreset("retro", "Retro", FilterCategory.VINTAGE, "80s camcorder magenta-amber tint", 0xFFEC4899, 0xFFF59E0B, brightness = 0.03f, contrast = 1.12f, saturation = 1.18f, warmth = 0.10f, tintArgb = 0x18EC4899, vignetteAlpha = 0.18f),
        VideoFilterPreset("film", "Film", FilterCategory.VINTAGE, "35mm celluloid grain contrast", 0xFF475569, 0xFFD97706, brightness = 0.02f, contrast = 1.15f, saturation = 0.92f, warmth = 0.06f, tintArgb = 0x120F172A, vignetteAlpha = 0.22f),
        VideoFilterPreset("cinematic", "Cinematic", FilterCategory.VINTAGE, "Teal shadows & warm highlights", 0xFF0E7490, 0xFFF97316, brightness = 0.02f, contrast = 1.20f, saturation = 1.08f, warmth = 0.04f, tintArgb = 0x160284C7, vignetteAlpha = 0.25f, isTrending = true),

        VideoFilterPreset("warm", "Warm", FilterCategory.WARM_GOLD, "Cozy amber daylight balance", 0xFFF97316, 0xFFFACC15, brightness = 0.05f, contrast = 1.04f, saturation = 1.10f, warmth = 0.24f, tintArgb = 0x1CFF8A00),
        VideoFilterPreset("cool", "Cool", FilterCategory.WARM_GOLD, "Crisp azure studio temperature", 0xFF0284C7, 0xFF38BDF8, brightness = 0.04f, contrast = 1.06f, saturation = 1.04f, warmth = -0.22f, tintArgb = 0x1C38BDF8),
        VideoFilterPreset("golden_hour", "Golden Hour", FilterCategory.WARM_GOLD, "Radiant sunset rim-light warmth", 0xFFF59E0B, 0xFFFB7185, brightness = 0.08f, contrast = 1.08f, saturation = 1.16f, warmth = 0.28f, tintArgb = 0x20FFB800, softGlow = 0.22f, isTrending = true),
        VideoFilterPreset("sunset", "Sunset", FilterCategory.WARM_GOLD, "Coral horizon & golden saturation", 0xFFEF4444, 0xFFF59E0B, brightness = 0.04f, contrast = 1.12f, saturation = 1.24f, warmth = 0.26f, tintArgb = 0x1EF97316),

        VideoFilterPreset("bw", "Black & White", FilterCategory.MONO_MOODY, "Classic high-clarity monochrome", 0xFF1E293B, 0xFFE2E8F0, brightness = 0.04f, contrast = 1.22f, saturation = 0f, isGrayscale = true),
        VideoFilterPreset("sepia", "Sepia", FilterCategory.MONO_MOODY, "Antique silver-brown darkroom print", 0xFF78350F, 0xFFFDE68A, brightness = 0.03f, contrast = 1.08f, saturation = 0.2f, warmth = 0.25f, isSepia = true, tintArgb = 0x1E92400E),
        VideoFilterPreset("moody", "Moody", FilterCategory.MONO_MOODY, "Deep editorial shadows & muted tone", 0xFF0F172A, 0xFF475569, brightness = -0.02f, contrast = 1.24f, saturation = 0.72f, warmth = -0.08f, vignetteAlpha = 0.30f),

        VideoFilterPreset("bright", "Bright", FilterCategory.TONE_SHARP, "High-key indoor & night illumination", 0xFFFEF08A, 0xFFFFFFFF, brightness = 0.22f, contrast = 1.08f, saturation = 1.10f, sharpness = 0.3f, isTrending = true),
        VideoFilterPreset("contrast", "Contrast", FilterCategory.TONE_SHARP, "Punchy blacks & crisp highlights", 0xFF111827, 0xFFF43F5E, brightness = 0.02f, contrast = 1.32f, saturation = 1.12f),
        VideoFilterPreset("saturation", "Saturation", FilterCategory.TONE_SHARP, "Ultra-vivid punchy color", 0xFFEC4899, 0xFF06B6D4, brightness = 0.04f, contrast = 1.08f, saturation = 1.42f),
        VideoFilterPreset("sharpness", "Sharpness", FilterCategory.TONE_SHARP, "Micro-contrast & HD edge clarity", 0xFF22D3EE, 0xFF0EA5E9, brightness = 0.05f, contrast = 1.22f, saturation = 1.08f, sharpness = 0.85f),

        VideoFilterPreset("food", "Food", FilterCategory.LIFESTYLE, "Appetizing crisp warm colors", 0xFFEA580C, 0xFF84CC16, brightness = 0.08f, contrast = 1.14f, saturation = 1.30f, warmth = 0.14f, sharpness = 0.4f),
        VideoFilterPreset("travel", "Travel", FilterCategory.LIFESTYLE, "Vibrant skies & rich greens", 0xFF0284C7, 0xFF10B981, brightness = 0.06f, contrast = 1.16f, saturation = 1.26f),
        VideoFilterPreset("landscape", "Landscape", FilterCategory.LIFESTYLE, "Dynamic horizon & crisp foliage", 0xFF059669, 0xFF38BDF8, brightness = 0.04f, contrast = 1.20f, saturation = 1.28f, sharpness = 0.5f),
        VideoFilterPreset("beauty", "Beauty", FilterCategory.LIFESTYLE, "Bright clear complexion & soft sheen", 0xFFFBCFE8, 0xFFFDE68A, brightness = 0.14f, contrast = 1.02f, saturation = 1.06f, warmth = 0.08f, softGlow = 0.30f, isTrending = true),

        VideoFilterPreset("glow", "Glow", FilterCategory.LIFESTYLE, "Luminous highlight bloom", 0xFFFEF9C3, 0xFFF472B6, brightness = 0.15f, contrast = 1.00f, saturation = 1.10f, warmth = 0.08f, softGlow = 0.42f, isTrending = true),
        VideoFilterPreset("soft_skin", "Soft Skin", FilterCategory.LIFESTYLE, "Velvet peach highlight balance", 0xFFFECDD3, 0xFFFED7AA, brightness = 0.12f, contrast = 0.96f, saturation = 1.04f, warmth = 0.10f, softGlow = 0.34f),
        VideoFilterPreset("smooth_skin", "Smooth Skin", FilterCategory.LIFESTYLE, "Silky portrait studio finish", 0xFFFDE047, 0xFFFB7185, brightness = 0.12f, contrast = 0.98f, saturation = 1.05f, warmth = 0.07f, softGlow = 0.38f),

        VideoFilterPreset("vivid", "Vivid", FilterCategory.TRENDING, "Pop clarity for creator reels", 0xFF06B6D4, 0xFF3B82F6, brightness = 0.06f, contrast = 1.16f, saturation = 1.32f, isTrending = true),
        VideoFilterPreset("blade_prism", "Blade Prism", FilterCategory.TRENDING, "Signature Blade crimson studio look", 0xFFE50914, 0xFF991B1B, brightness = 0.04f, contrast = 1.18f, saturation = 1.14f, tintArgb = 0x20E50914, vignetteAlpha = 0.16f, isTrending = true),
        VideoFilterPreset("neon_night", "Neon Night", FilterCategory.TRENDING, "Brightened night & electric cyber glow", 0xFF9333EA, 0xFF06B6D4, brightness = 0.12f, contrast = 1.22f, saturation = 1.28f, warmth = -0.14f, tintArgb = 0x1E8B5CF6, isTrending = true),
        VideoFilterPreset("peach_pop", "Peach Pop", FilterCategory.TRENDING, "Fresh pastel coral reel aesthetic", 0xFFFB7185, 0xFFFDBA74, brightness = 0.10f, contrast = 1.05f, saturation = 1.14f, warmth = 0.16f, softGlow = 0.24f, isTrending = true)
    )

    val defaultFilterNames: List<String> = allFilters.map { it.name }

    fun findByName(name: String): VideoFilterPreset =
        allFilters.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: allFilters.first()

    fun computeColorMatrix(
        filterName: String,
        filterIntensity: Float = 1f,
        extraBrightness: Float = 0f,
        extraContrast: Float = 1f,
        extraSaturation: Float = 1f,
        extraWarmth: Float = 0f,
        autoLowLightLift: Float = 0f,
        beautyBrightnessBoost: Float = 0f,
        beautyWarmth: Float = 0f
    ): ColorMatrix {
        val preset = findByName(filterName)
        val t = filterIntensity.coerceIn(0f, 1f)

        val totalBrightness = (preset.brightness * t + extraBrightness + autoLowLightLift + beautyBrightnessBoost * 0.30f)
            .coerceIn(-0.6f, 0.7f)
        val totalContrast = ((1f + (preset.contrast - 1f) * t) * extraContrast).coerceIn(0.45f, 1.9f)
        val totalSaturation = ((1f + (preset.saturation - 1f) * t) * extraSaturation).coerceIn(0f, 2.2f)
        val totalWarmth = (preset.warmth * t + extraWarmth + beautyWarmth * 0.18f).coerceIn(-0.6f, 0.6f)

        val result = ColorMatrix()

        // 1. Saturation
        val satMatrix = ColorMatrix().apply { setSaturation(totalSaturation) }
        result.postConcat(satMatrix)

        // 2. Sepia blend if requested
        if (preset.isSepia && t > 0.01f) {
            val s = t
            val inv = 1f - s
            val sepia = ColorMatrix(
                floatArrayOf(
                    inv + 0.393f * s, 0.769f * s, 0.189f * s, 0f, 0f,
                    0.349f * s, inv + 0.686f * s, 0.168f * s, 0f, 0f,
                    0.272f * s, 0.534f * s, inv + 0.131f * s, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            result.postConcat(sepia)
        }

        // 3. Contrast + Brightness + Warmth (R/B temperature shift)
        val translate = (totalBrightness * 255f) + 128f * (1f - totalContrast)
        val rScale = (totalContrast * (1f + totalWarmth * 0.22f)).coerceAtLeast(0.1f)
        val gScale = totalContrast
        val bScale = (totalContrast * (1f - totalWarmth * 0.22f)).coerceAtLeast(0.1f)

        val toneMatrix = ColorMatrix(
            floatArrayOf(
                rScale, 0f, 0f, 0f, translate,
                0f, gScale, 0f, 0f, translate,
                0f, 0f, bScale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        result.postConcat(toneMatrix)
        return result
    }

    fun buildMedia3Effects(
        filterName: String,
        filterIntensity: Float = 1f,
        extraBrightness: Float = 0f,
        extraContrast: Float = 1f,
        extraSaturation: Float = 1f,
        extraWarmth: Float = 0f,
        beautyBrightnessBoost: Float = 0f
    ): List<Effect> {
        val preset = findByName(filterName)
        val t = filterIntensity.coerceIn(0f, 1f)
        val effects = mutableListOf<Effect>()

        val brightness = (preset.brightness * t + extraBrightness + beautyBrightnessBoost * 0.25f)
            .coerceIn(-0.9f, 0.9f)
        if (kotlin.math.abs(brightness) > 0.015f) {
            effects.add(Brightness(brightness))
        }

        val contrastDelta = (((1f + (preset.contrast - 1f) * t) * extraContrast) - 1f)
            .coerceIn(-0.9f, 0.9f)
        if (kotlin.math.abs(contrastDelta) > 0.015f) {
            effects.add(Contrast(contrastDelta))
        }

        val satMul = ((1f + (preset.saturation - 1f) * t) * extraSaturation).coerceIn(0f, 2.2f)
        if (preset.isGrayscale && t > 0.7f) {
            effects.add(RgbFilter.createGrayscaleFilter())
        } else if (kotlin.math.abs(satMul - 1f) > 0.02f) {
            val satDelta = ((satMul - 1f) * 100f).coerceIn(-100f, 100f)
            effects.add(HslAdjustment.Builder().adjustSaturation(satDelta).build())
        }

        val warmth = (preset.warmth * t + extraWarmth).coerceIn(-0.5f, 0.5f)
        if (kotlin.math.abs(warmth) > 0.02f) {
            effects.add(
                RgbAdjustment.Builder()
                    .setRedScale((1f + warmth * 0.28f).coerceIn(0.6f, 1.5f))
                    .setBlueScale((1f - warmth * 0.28f).coerceIn(0.6f, 1.5f))
                    .build()
            )
        }
        return effects
    }

    fun swatchOverlayColor(filterName: String): Color {
        val preset = findByName(filterName)
        return if (preset.tintArgb != 0x00000000L) {
            Color(preset.tintArgb)
        } else if (preset.isGrayscale) {
            Color(0x38111827)
        } else if (preset.isSepia) {
            Color(0x2892400E)
        } else {
            Color.Transparent
        }
    }
}
