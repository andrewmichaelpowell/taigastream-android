//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class DeutschlandfunkProvider : MetadataProvider {
    override val pollInterval: Long = 15

    private enum class ApiStyle { NOVA, KULTUR }
    private data class ApiConfig(val url: String, val style: ApiStyle)

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return STREAM_TO_CONFIG.keys.any { s.contains(it) }
    }

    private fun config(streamUrl: HttpUrl): ApiConfig? {
        val s = streamUrl.toString()
        return STREAM_TO_CONFIG.entries.firstOrNull { s.contains(it.key) }?.value
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val config = config(streamUrl) ?: return
        NetworkClient.fetchBody(NetworkClient.get(config.url)) { body ->
            when (config.style) {
                ApiStyle.NOVA -> parseNova(body, onResult)
                ApiStyle.KULTUR -> parseKultur(body, onResult)
            }
        }
    }

    private fun parseNova(body: String, onResult: (MetadataResult) -> Unit) {
        val item = runCatching { JSONObject(body).optJSONObject("playlistItem") }.getOrNull()
            ?: return
        if (item.optString("type") != "Music") return
        val title = item.optString("title").trim()
        val artist = item.optString("artist").trim()
        if (title.isEmpty()) return
        onResult(MetadataResult(artist, title))
    }

    private fun parseKultur(body: String, onResult: (MetadataResult) -> Unit) {
        val (author, workTitle) = MetadataTextUtils.splitArtistTitle(body.trim())
        if (workTitle.isEmpty()) return
        onResult(MetadataResult(author, workTitle))
    }

    companion object {
        private val STREAM_TO_CONFIG: Map<String, ApiConfig> = mapOf(
            "st03.sslstream.dlf.de" to ApiConfig(
                "https://static.deutschlandfunknova.de/actions/dradio/playlist/onair", ApiStyle.NOVA
            ),
            "st03.dlf.de" to ApiConfig(
                "https://static.deutschlandfunknova.de/actions/dradio/playlist/onair", ApiStyle.NOVA
            ),
            "/dlf/03/" to ApiConfig(
                "https://static.deutschlandfunknova.de/actions/dradio/playlist/onair", ApiStyle.NOVA
            ),
            "st02.sslstream.dlf.de" to ApiConfig(
                "https://streamtext.dradio.de/drk_utf8.txt", ApiStyle.KULTUR
            ),
            "st02.dlf.de" to ApiConfig("https://streamtext.dradio.de/drk_utf8.txt", ApiStyle.KULTUR),
            "/dlf/02/" to ApiConfig("https://streamtext.dradio.de/drk_utf8.txt", ApiStyle.KULTUR),
        )
    }
}
