//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

/**
 * Ports `cleanMetadataString`/`junkMetadata`/`splitArtistTitle` from `StreamInfo`
 * (Taiga Stream Widget/WidgetView.swift:2338-2451). These are shared by nearly every provider to
 * strip hosting-platform noise (ISRC suffixes, bracketed codes, "visit us at ..." prefixes) and to
 * reject metadata blobs that are clearly not a real artist/title (raw query strings, ad markers,
 * template placeholders like `zc123` or `song_spot=`).
 */
object MetadataTextUtils {

    private val hostedPlatformPrefixPattern =
        Regex("(?i)^(visit us at \\S+\\s*-\\s*|\\[[^]]+]\\s*)")
    private val isrcPattern = Regex("\\s*-\\s*[A-Z][A-Z0-9]{7,11}$")
    private val trailingHyphenPattern = Regex("\\s*-\\s*$")
    private val bracketedCodePattern = Regex("\\s*\\[[A-Za-z0-9]{3,4}]\\s*$")

    fun cleanMetadataString(input: String): String {
        var cleaned = input

        hostedPlatformPrefixPattern.find(cleaned)?.let { match ->
            cleaned = cleaned.substring(match.range.last + 1)
        }
        isrcPattern.find(cleaned)?.let { match ->
            cleaned = cleaned.substring(0, match.range.first)
        }
        trailingHyphenPattern.find(cleaned)?.let { match ->
            cleaned = cleaned.substring(0, match.range.first)
        }
        bracketedCodePattern.find(cleaned)?.let { match ->
            cleaned = cleaned.substring(0, match.range.first)
        }

        while (cleaned.contains("  ")) {
            cleaned = cleaned.replace("  ", " ")
        }

        return cleaned.trim()
    }

    private val titleArtistEmptyPattern = Regex("title=\"[^\"]*\",artist=\"")
    private val zcPattern = Regex("^zc\\d+$", RegexOption.IGNORE_CASE)
    private val underscoreTokenPattern = Regex("^[a-z0-9]*_[a-z0-9_]+$", RegexOption.IGNORE_CASE)
    private val junkPhrasePattern = Regex("(?i)(spot\\s+block|ad\\s+break|commercial\\s+break)")
    private val keyValuePattern = Regex("\\w+\\s*=\\s*\"")

    fun isJunkMetadata(value: String): Boolean {
        val trimmed = value.trim()

        if (trimmed.isEmpty()) return true
        if (titleArtistEmptyPattern.containsMatchIn(trimmed)) return true
        if (trimmed.contains("song_spot=")) return true
        if (zcPattern.containsMatchIn(trimmed)) return true
        if (underscoreTokenPattern.containsMatchIn(trimmed)) return true
        if (trimmed.contains("://")) return true
        if (trimmed.startsWith("/") || trimmed.endsWith("/")) return true
        if (trimmed.split("/").size > 2) return true
        if (junkPhrasePattern.containsMatchIn(trimmed)) return true
        if (keyValuePattern.containsMatchIn(trimmed)) return true

        return false
    }

    private val separators = listOf(" — ", " – ", " - ", " / ", " · ", " | ")

    /** Returns (artist, title); artist is empty when no separator split cleanly. */
    fun splitArtistTitle(raw: String): Pair<String, String> {
        for (separator in separators) {
            val parts = raw.split(separator)
            if (parts.size >= 2) {
                val artist = cleanMetadataString(parts[0].trim())
                val title = cleanMetadataString(parts.drop(1).joinToString(separator).trim())
                if (artist.isNotEmpty() && title.isNotEmpty()) {
                    return artist to title
                }
            }
        }
        return "" to cleanMetadataString(raw.trim())
    }
}
