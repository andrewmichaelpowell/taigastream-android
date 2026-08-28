//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

/**
 * Ports `StreamInfo.parseMetadata` (Taiga Stream Widget/WidgetView.swift:2455-2636) — the
 * heuristics applied to a raw in-band ICY `StreamTitle` blob (as opposed to the structured JSON
 * each network-specific [MetadataProvider] returns). Used only as the fallback path when no
 * specific provider recognizes a station, i.e. self-hosted Icecast/Shoutcast stations.
 *
 * iOS receives this text via `AVMetadataItem`s keyed by `.commonKeyTitle`/`.commonKeyArtist`;
 * Media3 exposes the same raw ICY `StreamTitle` string through `IcyInfo.title`, with no separate
 * artist field, so [rawArtist] is normally null here but kept for parity with the original
 * two-field logic.
 */
object IcyMetadataParser {

    private val textFieldPattern = Regex("text=\"([^\"]*)\"")
    private val isrcPattern = Regex("^[A-Z][A-Z0-9]{7,11}$")
    private val hyphenSplitPattern = Regex("\\s*-\\s*")

    /** Returns (artist, title); either may be empty when nothing usable was found. */
    fun parse(rawTitle: String?, rawArtist: String? = null, useAggressiveParsing: Boolean = false): Pair<String, String> {
        var artist = ""
        var title = ""

        rawArtist?.let { raw ->
            val cleaned = MetadataTextUtils.cleanMetadataString(raw)
            artist = if (MetadataTextUtils.isJunkMetadata(cleaned)) "" else cleaned
        }

        rawTitle?.let { value ->
            var handled = false

            if (value.contains("text=") && value.contains("song_spot=")) {
                var parsedTitle = ""
                var parsedArtist = ""
                textFieldPattern.find(value)?.let { match ->
                    parsedTitle = match.value.removePrefix("text=\"").removeSuffix("\"").trim()
                }
                val separatorIndex = value.indexOf(" - text=\"")
                if (separatorIndex >= 0) {
                    parsedArtist = value.substring(0, separatorIndex).trim()
                }
                if (parsedTitle.isNotEmpty()) {
                    title = MetadataTextUtils.cleanMetadataString(parsedTitle)
                    if (parsedArtist.isNotEmpty()) {
                        artist = MetadataTextUtils.cleanMetadataString(parsedArtist)
                    }
                    handled = true
                }
            }

            if (!handled && value.contains("~")) {
                val parts = value.split("~").map { it.trim() }
                val parsedArtist = parts.getOrNull(0)
                    ?.let { MetadataTextUtils.cleanMetadataString(it) } ?: ""
                val parsedTitle = parts.getOrNull(1)
                    ?.let { MetadataTextUtils.cleanMetadataString(it) } ?: ""
                if (parsedArtist.isNotEmpty() && parsedTitle.isNotEmpty()) {
                    artist = if (MetadataTextUtils.isJunkMetadata(parsedArtist)) "" else parsedArtist
                    title = if (MetadataTextUtils.isJunkMetadata(parsedTitle)) "" else parsedTitle
                    handled = true
                }
            }

            if (!handled) {
                val parts = value.split(" - ")
                val lastPart = parts.lastOrNull()?.trim() ?: ""
                val lastIsIsrc = isrcPattern.matches(lastPart)

                if (lastIsIsrc || lastPart.isEmpty()) {
                    val cleanParts = parts.dropLast(1).map { it.trim() }
                    title = MetadataTextUtils.cleanMetadataString(cleanParts.firstOrNull() ?: "")
                    artist =
                        MetadataTextUtils.cleanMetadataString(cleanParts.drop(1).joinToString(" - "))
                } else {
                    val (parsedArtist, parsedTitle) = MetadataTextUtils.splitArtistTitle(value)
                    if (parsedArtist.isNotEmpty()) {
                        artist = parsedArtist
                        title = parsedTitle
                    } else {
                        title = parsedTitle.ifEmpty { MetadataTextUtils.cleanMetadataString(value) }
                    }
                }
            }
        }

        if (MetadataTextUtils.isJunkMetadata(title)) title = ""
        if (MetadataTextUtils.isJunkMetadata(artist)) artist = ""

        var artistFieldTitle = ""
        if (artist.contains(" — ") || artist.contains(" – ") || artist.contains(" - ")) {
            val (parsedArtist, parsedTitle) = MetadataTextUtils.splitArtistTitle(artist)
            if (parsedArtist.isNotEmpty() && parsedTitle.isNotEmpty()) {
                artist = parsedArtist
                artistFieldTitle = parsedTitle
            }
        } else if (useAggressiveParsing) {
            hyphenSplitPattern.find(artist)?.let { match ->
                val parsedArtist = MetadataTextUtils.cleanMetadataString(artist.substring(0, match.range.first))
                val parsedTitle = MetadataTextUtils.cleanMetadataString(artist.substring(match.range.last + 1))
                if (parsedArtist.isNotEmpty() && parsedTitle.isNotEmpty()) {
                    artist = parsedArtist
                    artistFieldTitle = parsedTitle
                }
            }
        }

        if (artistFieldTitle.isNotEmpty()) {
            title = artistFieldTitle
        } else if (artist.isNotEmpty() && title.isNotEmpty()) {
            val separators = buildList {
                addAll(listOf(" — ", " – ", " - ", " / ", " · ", " | "))
                if (useAggressiveParsing) add("-")
            }

            for (separator in separators) {
                if (!title.contains(separator)) continue
                val parts = title.split(separator).map { it.trim() }
                if (parts.size <= 1) continue

                val firstPart = parts[0]
                val secondPart = parts[1]

                val resolved = if (firstPart.equals(artist, ignoreCase = true)) {
                    MetadataTextUtils.cleanMetadataString(secondPart)
                } else {
                    MetadataTextUtils.cleanMetadataString(firstPart)
                }
                if (resolved.isNotEmpty()) {
                    title = resolved
                    break
                }
            }
        }

        return artist to title
    }
}
