//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import java.net.URLEncoder
import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class VirginRadioItalyProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean = streamUrl.host.contains("unitedradio.it")

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val httpsStreamUrl = streamUrl.newBuilder().scheme("https").build()
        val encodedStream = URLEncoder.encode(httpsStreamUrl.toString(), "UTF-8")
        val apiUrl = "https://www.virginradio.it/wp-json/mediaset-mediaplayer/v1/getStreamInfo?stream=$encodedStream"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            if (!json.optBoolean("success")) return@fetchBody
            val artist = json.optString("artist").trim()
            val title = json.optString("title").trim()
            if (title.isEmpty()) return@fetchBody
            onResult(MetadataResult(artist, title))
        }
    }
}
