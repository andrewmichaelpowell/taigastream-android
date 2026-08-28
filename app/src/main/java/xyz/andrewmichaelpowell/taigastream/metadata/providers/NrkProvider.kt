//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `NRKProvider`. */
class NrkProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean =
        streamUrl.host.contains("nrk-live-radio-world.akamaized.net")

    private fun apiUrl(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString()
        val key = SORTED_KEYS.firstOrNull { s.contains(it) } ?: return null
        return CHANNEL_MAP[key]
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl = apiUrl(streamUrl) ?: return
        NetworkClient.fetchBody(
            NetworkClient.get(apiUrl, headers = mapOf("Accept" to "application/json"))
        ) { body ->
            val elements = runCatching { JSONArray(body) }.getOrNull() ?: return@fetchBody
            val list = (0 until elements.length()).mapNotNull { elements.optJSONObject(it) }
            val current = list.lastOrNull {
                it.optString("relativeTimeType") == "Present" && it.optString("type") == "Music"
            } ?: list.lastOrNull {
                it.optString("relativeTimeType") == "Past" && it.optString("type") == "Music"
            } ?: return@fetchBody

            val title = current.optString("title").trim()
            val artist = current.optString("description").trim()
            if (title.isEmpty()) return@fetchBody

            val imageUrl = current.optString("imageUrl").ifEmpty { null }
            onResult(MetadataResult(artist, title, imageUrl))
        }
    }

    companion object {
        private val CHANNEL_MAP: Map<String, String> = mapOf(
            "p1" to "https://psapi.nrk.no/channels/p1/liveelements",
            "p1pluss" to "https://psapi.nrk.no/channels/p1pluss/liveelements",
            "p2" to "https://psapi.nrk.no/channels/p2/liveelements",
            "p3" to "https://psapi.nrk.no/channels/p3/liveelements",
            "p3musikk" to "https://psapi.nrk.no/channels/p3musikk/liveelements",
            "mp3" to "https://psapi.nrk.no/channels/mp3/liveelements",
            "nyheter" to "https://psapi.nrk.no/channels/nyheter/liveelements",
            "radio_super" to "https://psapi.nrk.no/channels/radio_super/liveelements",
            "klassisk" to "https://psapi.nrk.no/channels/klassisk/liveelements",
            "sapmi" to "https://psapi.nrk.no/channels/sapmi/liveelements",
            "jazz" to "https://psapi.nrk.no/channels/jazz/liveelements",
            "folkemusikk" to "https://psapi.nrk.no/channels/folkemusikk/liveelements",
            "sport" to "https://psapi.nrk.no/channels/sport/liveelements",
        )
        private val SORTED_KEYS = CHANNEL_MAP.keys.sortedByDescending { it.length }
    }
}
