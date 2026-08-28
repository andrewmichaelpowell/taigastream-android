//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONArray
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `RTLRadioProvider`. */
class RtlRadioProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val host = streamUrl.host
        return streamUrl.toString().contains("streamabc.net") && host.contains("rtl")
    }

    private fun channelKey(streamUrl: HttpUrl): String? {
        val pathFirst = streamUrl.pathSegments.firstOrNull { it.isNotEmpty() } ?: return null
        val parts = pathFirst.split("-")
        val key = parts.drop(1).takeWhile {
            it.toIntOrNull() == null && it != "mp3" && it != "aac" &&
                it !in setOf("128", "64", "192", "320")
        }.joinToString("-")
        return key.ifEmpty { null }
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val channelKey = channelKey(streamUrl) ?: return
        val apiUrl = "https://www.rtlradio.de/services/program-info/live/lux"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val channels = runCatching { JSONArray(body) }.getOrNull() ?: return@fetchBody
            val normalizedTarget = channelKey.replace("-", "")
            var match: org.json.JSONObject? = null
            for (i in 0 until channels.length()) {
                val channel = channels.optJSONObject(i) ?: continue
                if (channel.optString("channelKey").replace("-", "") == normalizedTarget) {
                    match = channel
                    break
                }
            }
            val histories = match?.optJSONArray("playHistories") ?: return@fetchBody
            if (histories.length() == 0) return@fetchBody
            val track = histories.optJSONObject(0)?.optJSONObject("track") ?: return@fetchBody
            val artist = track.optString("artist").trim()
            val title = track.optString("title").trim()
            val artworkUrl = track.optString("itunesCover").ifEmpty { null }
            onResult(MetadataResult(artist, title, artworkUrl))
        }
    }
}
