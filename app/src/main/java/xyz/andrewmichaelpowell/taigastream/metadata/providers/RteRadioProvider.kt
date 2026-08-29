//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class RteRadioProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return STREAM_TO_API.keys.any { s.contains(it) }
    }

    private fun apiUrl(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString()
        return STREAM_TO_API.entries.firstOrNull { s.contains(it.key) }?.value
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl = apiUrl(streamUrl) ?: return
        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val nowPlaying = json.optJSONArray("nowplaying") ?: return@fetchBody
            val list = (0 until nowPlaying.length()).mapNotNull { nowPlaying.optJSONObject(it) }
            val current = list.firstOrNull { it.optString("status") == "playing" }
                ?: list.firstOrNull()
                ?: return@fetchBody

            val artist = current.optString("artist").trim()
            val title = current.optString("title").trim()
            if (title.isEmpty()) return@fetchBody

            val artworkUrl = current.optString("imageUrl").ifEmpty { null }
            onResult(MetadataResult(artist, title, artworkUrl))
        }
    }

    companion object {
        private val STREAM_TO_API: Map<String, String> = mapOf(
            "rte.ie/radio1" to "https://onair.radioapi.io/rte/rteradio1/onair.json",
            "streamtheworld.com/RTE_RADIO1" to "https://onair.radioapi.io/rte/rteradio1/onair.json",
            "rte.ie/2fm" to "https://onair.radioapi.io/rte/rte2fm/onair.json",
            "streamtheworld.com/RTE_2FM" to "https://onair.radioapi.io/rte/rte2fm/onair.json",
            "rte.ie/lyricfm" to "https://onair.radioapi.io/rte/rtelyricfm/onair.json",
            "streamtheworld.com/RTE_LYRIC" to "https://onair.radioapi.io/rte/rtelyricfm/onair.json",
            "rte.ie/rnag" to "https://onair.radioapi.io/rte/rteraidionagaeltachta/onair.json",
            "streamtheworld.com/RTE_RNAG" to
                "https://onair.radioapi.io/rte/rteraidionagaeltachta/onair.json",
            "rte.ie/gold" to "https://onair.radioapi.io/rte/rtegold/onair.json",
            "streamtheworld.com/RTE_GOLD" to "https://onair.radioapi.io/rte/rtegold/onair.json",
        )
    }
}
