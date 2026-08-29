//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class SomaFmProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val host = streamUrl.host
        return streamUrl.toString().contains("somafm.com") && host.contains("somafm")
    }

    private fun apiUrl(streamUrl: HttpUrl): String? {
        val mountName = streamUrl.pathSegments.firstOrNull { it.isNotEmpty() } ?: ""
        val channel = mountName.split("-").firstOrNull() ?: ""
        if (channel.isEmpty()) return null
        return "https://somafm.com/songs/$channel.json"
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl = apiUrl(streamUrl) ?: return
        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val songs = json.optJSONArray("songs") ?: return@fetchBody
            if (songs.length() == 0) return@fetchBody
            val first = songs.optJSONObject(0) ?: return@fetchBody
            val title = first.optString("title").trim()
            val artist = first.optString("artist").trim()
            onResult(MetadataResult(artist, title))
        }
    }
}
