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

/**
 * Ports `IcecastProvider` — the catch-all fallback that queries an Icecast server's own
 * `status-json.xsl` for its current source title. Must stay last in provider precedence since it
 * matches every stream URL.
 *
 * The iOS version unconditionally forces `https` for this status check, and this originally
 * matched that — but that breaks real stations on shared CDN hosting where the TLS certificate
 * doesn't match the per-customer hostname (confirmed via `lostcoast.streamguys.us`, whose
 * certificate is issued for `*.streamguys1.com`, causing a hostname-verification failure on every
 * request). This tries the stream's own scheme first — matching what's already known to work well
 * enough to serve the audio — and falls back to the other scheme only if that fails.
 */
class IcecastProvider : MetadataProvider {
    override val pollInterval: Long = 15

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
            val (parsedArtist, parsedTitle) = MetadataTextUtils.splitArtistTitle(title)
            val resolvedTitle = parsedTitle.ifEmpty { MetadataTextUtils.cleanMetadataString(title) }
            onResult(MetadataResult(parsedArtist, resolvedTitle))
        }
    }
}
