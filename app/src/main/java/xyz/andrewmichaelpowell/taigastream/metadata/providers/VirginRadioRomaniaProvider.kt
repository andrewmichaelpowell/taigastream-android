//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class VirginRadioRomaniaProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean = streamUrl.host.contains("astreaming.edi.ro")

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        NetworkClient.fetchBody(
            NetworkClient.get("https://virginradio.ro/track_info.json")
        ) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val songs = json.optJSONArray("songs") ?: return@fetchBody
            if (songs.length() == 0) return@fetchBody
            val current = songs.optJSONObject(0) ?: return@fetchBody
            val artist = current.optString("artist").trim()
            val title = current.optString("track").trim()
            if (title.isEmpty()) return@fetchBody
            onResult(MetadataResult(artist, title))
        }
    }
}
