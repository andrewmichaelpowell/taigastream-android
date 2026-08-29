//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONArray
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class AudioAddictProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val network = streamUrl.queryParameter("network") ?: return false
        return network in NETWORKS
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val network = streamUrl.queryParameter("network") ?: return
        val channelId = streamUrl.queryParameter("channel_id") ?: return
        val apiUrl = "https://api.audioaddict.com/v1/$network/currently_playing"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val channels = runCatching { JSONArray(body) }.getOrNull() ?: return@fetchBody
            for (i in 0 until channels.length()) {
                val channel = channels.optJSONObject(i) ?: continue
                if (channel.opt("channel_id")?.toString() != channelId) continue
                val track = channel.optJSONObject("track") ?: return@fetchBody
                val artist = track.optString("display_artist").trim()
                val title = track.optString("display_title").trim()
                onResult(MetadataResult(artist, title))
                return@fetchBody
            }
        }
    }

    companion object {
        private val NETWORKS =
            setOf("di", "jazzradio", "rockradio", "radiotunes", "classicalradio", "zenradio")
    }
}
