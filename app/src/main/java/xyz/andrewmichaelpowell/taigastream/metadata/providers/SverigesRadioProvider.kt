//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `SverigesRadioProvider` (Swedish public radio). */
class SverigesRadioProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString().lowercase()
        return (s.contains(".sr.se") || s.contains("sverigesradio")) && channelId(streamUrl) != null
    }

    private fun channelId(streamUrl: HttpUrl): Int? {
        val s = streamUrl.toString().lowercase()
        val key = STREAM_TO_CHANNEL_ID.keys.sortedByDescending { it.length }
            .firstOrNull { s.contains(it) }
        return key?.let { STREAM_TO_CHANNEL_ID[it] }
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val channelId = channelId(streamUrl) ?: return
        val apiUrl = "https://api.sr.se/api/v2/playlists/rightnow?channelid=$channelId&format=json"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val song = json.optJSONObject("playlist")?.optJSONObject("song") ?: return@fetchBody
            val artist = song.optString("artist").trim()
            val title = song.optString("title").trim()
            if (title.isEmpty() || MetadataTextUtils.isJunkMetadata(title)) return@fetchBody
            onResult(MetadataResult(artist, title))
        }
    }

    companion object {
        private val STREAM_TO_CHANNEL_ID: Map<String, Int> = mapOf(
            "/p1/" to 132, "/p1-" to 132, "sr-p1" to 132,
            "/p2/" to 163, "/p2-" to 163, "sr-p2" to 163,
            "/p3/" to 164, "/p3-" to 164, "sr-p3" to 164,
            "p4-blekinge" to 204,
            "p4-dalarna" to 201,
            "p4-gavleborg" to 207,
            "p4-gotland" to 209,
            "p4-halland" to 206,
            "p4-jamtland" to 210,
            "p4-jonkoping" to 205,
            "p4-kalmar" to 214,
            "p4-kronoberg" to 213,
            "p4-norrbotten" to 212,
            "p4-skaraborg" to 530,
            "p4-skane" to 211,
            "p4-stockholm" to 203,
            "p4-sormland" to 215,
            "p4-uppland" to 216,
            "p4-varmland" to 196,
            "p4-vast" to 197,
            "p4-vasterbotten" to 200,
            "p4-vasternorrland" to 202,
            "p4-vastmanland" to 208,
            "p4-ostergotland" to 217,
            "sr-p4" to 500,
            "sr-extra" to 666,
            "p6-" to 2576,
            "sr-p6" to 2576,
            "lc/p1" to 132,
            "lc/p2" to 163,
            "lc/p3" to 164,
            "edge1.sr.se/p1" to 132,
            "edge1.sr.se/p2" to 163,
            "edge1.sr.se/p3" to 164,
            "edge2.sr.se/p1" to 132,
            "edge2.sr.se/p2" to 163,
            "edge3.sr.se/p3" to 164,
        )
    }
}
