//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class IcecastProvider : MetadataProvider {
    override val pollInterval: Long = 15

    private val isrcPattern = Regex("^[A-Z][A-Z0-9]{7,11}$")

    override fun matches(streamUrl: HttpUrl): Boolean = true

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val primaryScheme = streamUrl.scheme
        val fallbackScheme = if (primaryScheme == "https") "http" else "https"
        pollWithScheme(streamUrl, primaryScheme, onResult) {
            pollWithScheme(streamUrl, fallbackScheme, onResult) {}
        }
    }

    private fun pollWithScheme(
        streamUrl: HttpUrl,
        scheme: String,
        onResult: (MetadataResult) -> Unit,
        onFailure: () -> Unit,
    ) {
        val statusUrl = streamUrl.newBuilder()
            .scheme(scheme)
            .encodedPath("/status-json.xsl")
            .query(null)
            .build()
        val mountPath = streamUrl.encodedPath

        NetworkClient.fetchBody(NetworkClient.get(statusUrl.toString()), onFailure = onFailure) { body ->
            val iceStats = runCatching { JSONObject(body).optJSONObject("icestats") }.getOrNull()
                ?: return@fetchBody onFailure()

            val sources: List<JSONObject> = when (val raw = iceStats.opt("source")) {
                is JSONArray -> (0 until raw.length()).mapNotNull { raw.optJSONObject(it) }
                is JSONObject -> listOf(raw)
                null -> emptyList()
                else -> emptyList()
            }
            val source = sources.firstOrNull { it.optString("listenurl").endsWith(mountPath) }
                ?: sources.firstOrNull()
                ?: return@fetchBody
            if (!source.has("title")) return@fetchBody
            val rawTitle = source.optString("title")

            val rawArtist = source.optString("artist").trim()
            if (rawArtist.isNotEmpty()) {
                val artist = MetadataTextUtils.cleanMetadataString(rawArtist)
                val title = MetadataTextUtils.cleanMetadataString(rawTitle.trim())
                if (title.isEmpty()) return@fetchBody
                onResult(MetadataResult(artist, title))
                return@fetchBody
            }

            val title = rawTitle.trim()
            if (title.isEmpty()) return@fetchBody

            val hyphenParts = title.split(" - ")
            val lastPart = hyphenParts.lastOrNull()?.trim() ?: ""
            if (hyphenParts.size >= 3 && isrcPattern.matches(lastPart)) {
                val cleanParts = hyphenParts.dropLast(1).map { it.trim() }
                val isrcTitle = MetadataTextUtils.cleanMetadataString(cleanParts.firstOrNull() ?: "")
                val isrcArtist = MetadataTextUtils.cleanMetadataString(cleanParts.drop(1).joinToString(" - "))
                if (isrcTitle.isNotEmpty()) {
                    onResult(MetadataResult(isrcArtist, isrcTitle))
                    return@fetchBody
                }
            }

            val (parsedArtist, parsedTitle) = MetadataTextUtils.splitArtistTitle(title)
            val resolvedTitle = parsedTitle.ifEmpty { MetadataTextUtils.cleanMetadataString(title) }
            onResult(MetadataResult(parsedArtist, resolvedTitle))
        }
    }
}
