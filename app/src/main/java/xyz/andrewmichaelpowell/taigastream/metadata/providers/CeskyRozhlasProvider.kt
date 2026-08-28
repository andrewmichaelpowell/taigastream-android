//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `CeskyRozhlasProvider` (Czech Radio). */
class CeskyRozhlasProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val host = streamUrl.host
        if (!host.contains("amp.cesnet.cz") && !host.contains("rozhlas.stream")) return false
        return stationCode(streamUrl) != null
    }

    private fun stationCode(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString().lowercase()
        val key = STREAM_TO_STATION.keys.sortedByDescending { it.length }.firstOrNull { s.contains(it) }
        return key?.let { STREAM_TO_STATION[it] }
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val station = stationCode(streamUrl) ?: return
        val apiUrl = "https://api.rozhlas.cz/data/v2/playlist/now/$station.json"

        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val playlistData = json.optJSONObject("data") ?: return@fetchBody
            if (playlistData.optString("status") != "onair") return@fetchBody

            val artist = playlistData.optString("interpret").trim()
            val title = playlistData.optString("track").trim()
            if (title.isEmpty() || MetadataTextUtils.isJunkMetadata(title)) return@fetchBody

            val files = playlistData.optJSONArray("files")
            val artworkUrl = if (files != null && files.length() > 0) {
                files.optJSONObject(0)?.optString("asset")?.ifEmpty { null }
            } else {
                null
            }

            onResult(MetadataResult(artist, title, artworkUrl))
        }
    }

    companion object {
        private val STREAM_TO_STATION: Map<String, String> = mapOf(
            "cro-radiozurnal" to "radiozurnal",
            "cro-dvojka" to "dvojka",
            "cro-vltava" to "vltava",
            "cro-radio3" to "radio3",
            "cro-plus" to "plus",
            "cro-jazz" to "jazz",
            "cro-d-dur" to "ddur",
            "cro-radio-wave" to "radiowave",
            "cro-radiozurnal-sport" to "radiozurnalsport",
            "cro-radio-junior-zs" to "radiojuniorzs",
            "cro-radio-junior" to "radiojunior",
            "cro-radio-prague-int" to "radiopragueint",
            "radio_zurnal_sport" to "radiozurnalsport",
            "radio_junior_zs" to "radiojuniorzs",
            "radio_prague_int" to "radiopragueint",
            "radio_junior" to "radiojunior",
            "radio_zurnal" to "radiozurnal",
            "radio_wave" to "radiowave",
            "radio3" to "radio3",
            "dvojka" to "dvojka",
            "vltava" to "vltava",
            "d_dur" to "ddur",
            "jazz" to "jazz",
            "plus" to "plus",
        )
    }
}
