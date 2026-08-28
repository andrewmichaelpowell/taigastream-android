//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import java.net.URLDecoder
import okhttp3.HttpUrl
import org.json.JSONArray
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `VirginRadioOmanProvider` — parses a JSONP-ish "played" feed with a query-string payload. */
class VirginRadioOmanProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean =
        streamUrl.host == "uk5.internet-radio.com" && streamUrl.port == 8115

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl =
            "http://uk5.internet-radio.com:8115/played?sid=1&type=json&callback=cb&_=${System.currentTimeMillis()}"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val start = body.indexOf('[')
            val end = body.lastIndexOf(']')
            if (start < 0 || end < 0 || end < start) return@fetchBody
            val entries = runCatching { JSONArray(body.substring(start, end + 1)) }.getOrNull()
                ?: return@fetchBody
            if (entries.length() == 0) return@fetchBody

            val metadata = entries.optJSONObject(0)?.optJSONObject("metadata") ?: return@fetchBody
            val urlString = metadata.optString("url")
            if (urlString.isEmpty()) return@fetchBody

            val queryString = if (urlString.startsWith("&")) urlString.substring(1) else urlString
            val params = mutableMapOf<String, String>()
            for (pair in queryString.split("&")) {
                val parts = pair.split("=")
                if (parts.size != 2) continue
                val decoded = runCatching {
                    URLDecoder.decode(parts[1].replace("+", " "), "UTF-8")
                }.getOrDefault(parts[1])
                params[parts[0]] = decoded
            }

            val artist = (params["artist"] ?: "").trim()
            val title = (params["title"] ?: "").trim()
            if (title.isEmpty()) return@fetchBody
            onResult(MetadataResult(artist, title))
        }
    }
}
