package com.olaph.xdm.browser

/**
 * Identifies public TikTok and Instagram post URLs.
 * Does not bypass login, private content, or access restrictions.
 */
object SocialUrlResolver {

    enum class Platform {
        TIKTOK,
        INSTAGRAM
    }

    data class SocialUrl(
        val platform: Platform,
        val originalUrl: String
    )

    fun parse(rawUrl: String): SocialUrl? {
        val uri = runCatching {
            android.net.Uri.parse(rawUrl.trim())
        }.getOrNull() ?: return null

        if (uri.scheme !in listOf("http", "https")) return null

        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null

        return when (host) {
            "tiktok.com", "m.tiktok.com" ->
                if (uri.pathSegments.any { it == "video" }) {
                    SocialUrl(Platform.TIKTOK, uri.toString())
                } else null

            "instagram.com" ->
                if (uri.pathSegments.firstOrNull() in
                    listOf("p", "reel", "reels", "tv")
                ) {
                    SocialUrl(Platform.INSTAGRAM, uri.toString())
                } else null

            else -> null
        }
    }
}
