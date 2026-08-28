//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `RadioSwissProvider` (Radio Swiss Pop/Jazz/Classic). */
class RadioSwissProvider : MetadataProvider {
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
            val metadata = json.optJSONObject("channel")
                ?.optJSONObject("playingnow")
                ?.optJSONObject("current")
                ?.optJSONObject("metadata")
                ?: return@fetchBody

            val title = metadata.optString("title").trim()
            if (title.isEmpty()) return@fetchBody

            val artist = listOf(metadata.optString("artist"), metadata.optString("composer"))
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: ""

            onResult(MetadataResult(artist, title))
        }
    }

    companion object {
        private val STREAM_TO_API: Map<String, String> = mapOf(
            "srg-ssr.ch/srgssr/rsp" to "https://api.radioswisspop.ch/api/v1/rsp/en/current",
            "srg-ssr.ch/m/rsp" to "https://api.radioswisspop.ch/api/v1/rsp/en/current",
            "srg-ssr.ch/rsp" to "https://api.radioswisspop.ch/api/v1/rsp/en/current",
            "radioswisspop.ch" to "https://api.radioswisspop.ch/api/v1/rsp/en/current",
            "srg-ssr.ch/srgssr/rsj" to "https://api.radioswissjazz.ch/api/v1/rsj/en/current",
            "srg-ssr.ch/m/rsj" to "https://api.radioswissjazz.ch/api/v1/rsj/en/current",
            "srg-ssr.ch/rsj" to "https://api.radioswissjazz.ch/api/v1/rsj/en/current",
            "radioswissjazz.ch" to "https://api.radioswissjazz.ch/api/v1/rsj/en/current",
            "srg-ssr.ch/srgssr/rsc" to "https://api.radioswissclassic.ch/api/v1/rsc/en/current",
            "srg-ssr.ch/m/rsc" to "https://api.radioswissclassic.ch/api/v1/rsc/en/current",
            "srg-ssr.ch/rsc" to "https://api.radioswissclassic.ch/api/v1/rsc/en/current",
            "radioswissclassic.ch" to "https://api.radioswissclassic.ch/api/v1/rsc/en/current",
        )
    }
}
