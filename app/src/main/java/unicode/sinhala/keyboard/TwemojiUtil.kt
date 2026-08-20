package unicode.sinhala.keyboard

/**
 * Maps a standard unicode emoji string to its corresponding Twemoji CDN image URL.
 *
 * Twemoji (https://github.com/jdecked/twemoji) is Twitter's/community-maintained
 * open-source emoji artwork, licensed CC-BY 4.0 - free to fetch, cache and display.
 * Filenames are the emoji's codepoints in lowercase hex, joined by "-", with the
 * VARIATION SELECTOR-16 (U+FE0F) stripped out - except for keycap sequences
 * (e.g. 1️⃣, #️⃣, *️⃣) where Twemoji keeps it.
 */
object TwemojiUtil {

    // CDN mirror of the Twemoji asset repo (72x72 PNGs).
    private const val BASE_URL = "https://cdn.jsdelivr.net/gh/jdecked/twemoji@latest/assets/72x72/"

    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private const val COMBINING_ENCLOSING_KEYCAP = 0x20E3
    private val KEYCAP_BASES = setOf(
        '0'.code, '1'.code, '2'.code, '3'.code, '4'.code,
        '5'.code, '6'.code, '7'.code, '8'.code, '9'.code,
        '#'.code, '*'.code
    )

    /** Extracts the sequence of Unicode codepoints that make up [emoji]. */
    private fun codePoints(emoji: String): List<Int> {
        val points = mutableListOf<Int>()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            points.add(cp)
            i += Character.charCount(cp)
        }
        return points
    }

    /** Builds the Twemoji filename (without extension) for [emoji], e.g. "1f600". */
    fun codepointFileName(emoji: String): String {
        val points = codePoints(emoji)
        val isKeycap = points.size >= 2 &&
                points[0] in KEYCAP_BASES &&
                points.contains(COMBINING_ENCLOSING_KEYCAP)

        val filtered = if (isKeycap) {
            points // keycap sequences keep FE0F
        } else {
            points.filter { it != VARIATION_SELECTOR_16 }
        }

        return filtered.joinToString("-") { Integer.toHexString(it) }
    }

    /** Full CDN URL for the Twemoji PNG that represents [emoji]. */
    fun urlFor(emoji: String): String = "$BASE_URL${codepointFileName(emoji)}.png"
}
