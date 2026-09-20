package com.lo.imagine.data

/** Text adaptation only; does not change the selected API model. */
enum class NaiProfile(val id: String, val label: String) {
    V45_FULL("v45_full", "4.5 Full"), V45_CURATED("v45_curated", "4.5 Curated"),
    V5_FULL("v5_full", "5 Full"), V5_CURATED("v5_curated", "5 Curated");
    val isV5 get() = this == V5_FULL || this == V5_CURATED
}

data class NaiOptions(
    val modeEnabled: Boolean = false,
    val profileId: String = "auto",
    // Relay may inject its own tags. Explicit opt-in prevents hidden duplication.
    val quality: String = "off",
    val negative: String = "off",
    val artistsEnabled: Boolean = true,
    val styleEnabled: Boolean = true,
    val disabledArtists: List<String> = emptyList()
)

fun isNaiModel(model: String): Boolean {
    val m = model.trim().lowercase()
    return m.contains("novelai") || m.startsWith("nai") || m.contains("nai-diff")
}

fun resolveNaiProfile(model: String, options: NaiOptions = NaiOptions()): NaiProfile? {
    if (!options.modeEnabled) return null
    if (options.profileId == "off") return null
    if (options.profileId != "auto") return NaiProfile.entries.firstOrNull { it.id == options.profileId }
    if (!isNaiModel(model)) return null
    val m = model.lowercase()
    val curated = m.contains("curated")
    return when {
        Regex("""4[._-]5(?:\D|$)""").containsMatchIn(m) ->
            if (curated) NaiProfile.V45_CURATED else NaiProfile.V45_FULL
        Regex("""(?:^|[^a-z0-9])v?5(?:\D|$)|(?:novelai|nai)v?5(?:\D|$)""").containsMatchIn(m) ->
            if (curated) NaiProfile.V5_CURATED else NaiProfile.V5_FULL
        else -> null
    }
}

fun naiQualityTags(profile: NaiProfile, mode: String): String = when {
    mode == "off" -> ""
    profile.isV5 && mode == "light" -> "very aesthetic, amazing quality, no text"
    profile.isV5 -> "very aesthetic, masterpiece, no text"
    profile == NaiProfile.V45_FULL -> "location, very aesthetic, masterpiece, no text"
    else -> "location, masterpiece, no text, -0.8::feet::, rating:general"
}

fun naiNegativeTags(profile: NaiProfile, mode: String): String {
    if (mode == "off") return ""
    if (profile == NaiProfile.V45_CURATED) return if (mode == "heavy")
        "blurry, lowres, upscaled, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, halftone, multiple views, logo, too many watermarks, negative space, blank page"
    else "blurry, lowres, upscaled, artistic error, scan artifacts, jpeg artifacts, logo, too many watermarks, negative space, blank page"
    if (mode == "heavy") return "lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, screentone, multiple views, logo, too many watermarks, negative space, blank page"
    if (profile.isV5) return "lowres, bad hands, bad anatomy, artistic error, sepia, white haze, worst quality, very displeasing, jpeg artifacts, 0::ai-generated::"
    return "lowres, artistic error, scan artifacts, worst quality, bad quality, jpeg artifacts, multiple views, very displeasing, too many watermarks, negative space, blank page"
}
