package com.hypermusic.core.utils

/**
 * Semantic quality tiers for media artwork rendering across different UI viewports.
 * Maps directly to Google CDN cropping, resolution, and compression parameters.
 */
enum class ArtworkQuality(val sizeParam: String) {
    THUMBNAIL("w240-h240-c-l90-rj"),
    CARD("w544-h544-c-l90-rj"),
    VIDEO_CARD("w1200-h675-c-l90-rj"),
    HERO("w1200-h1200-c-l90-rj")
}

/**
 * Resolves artwork URLs deterministically based on target viewport quality tier.
 * Handles Google CDN dynamic resizing/cropping and YouTube video CDN resolution routing.
 */
object ArtworkUrlResolver {
    private val GOOGLE_SIZE_REGEX = Regex("=[sw]\\d+.*$")

    fun fallbackUrls(url: String): List<String> {
        if (!url.startsWith("https://i.ytimg.com/vi/") && !url.startsWith("http://i.ytimg.com/vi/")) return emptyList()
        val base = url.substringBeforeLast('/')
        val names = listOf("maxresdefault.jpg", "sddefault.jpg", "hqdefault.jpg", "mqdefault.jpg")
        val index = names.indexOf(url.substringAfterLast('/'))
        return if (index < 0) emptyList() else names.drop(index + 1).map { "$base/$it" }
    }

    fun higherQualityUrls(url: String): List<String> {
        val qualities = when {
            resolveUrl(url, ArtworkQuality.THUMBNAIL) == url -> listOf(ArtworkQuality.CARD, ArtworkQuality.HERO)
            resolveUrl(url, ArtworkQuality.CARD) == url -> listOf(ArtworkQuality.HERO)
            else -> emptyList()
        }
        // 4:3 YouTube thumbnails can contain different padding from the 16:9 endpoint.
        return qualities.mapNotNull { resolveUrl(url, it) }.distinct().filter {
            it != url && (!url.contains("i.ytimg.com/vi/") || it.endsWith("/maxresdefault.jpg"))
        }
    }

    fun resolveUrl(url: String?, quality: ArtworkQuality = ArtworkQuality.CARD): String? {
        if (url.isNullOrBlank()) return null

        // 1. YouTube Video Thumbnails (i.ytimg.com/vi/)
        // Uses native 16:9 / 4:3 endpoints matched to display density
        if (url.contains("i.ytimg.com/vi/")) {
            val clean = if (url.contains("?")) url.substringBefore("?") else url
            val base = clean.substringBeforeLast("/")
            return when (quality) {
                ArtworkQuality.HERO, ArtworkQuality.VIDEO_CARD -> "$base/maxresdefault.jpg"
                ArtworkQuality.CARD -> "$base/sddefault.jpg"
                ArtworkQuality.THUMBNAIL -> "$base/mqdefault.jpg"
            }
        }

        if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
            val spec = "=" + quality.sizeParam
            return if (url.contains(GOOGLE_SIZE_REGEX)) {
                url.replace(GOOGLE_SIZE_REGEX, spec)
            } else if (!url.endsWith("=")) {
                url + spec
            } else {
                url
            }
        }

        return url
    }
}
