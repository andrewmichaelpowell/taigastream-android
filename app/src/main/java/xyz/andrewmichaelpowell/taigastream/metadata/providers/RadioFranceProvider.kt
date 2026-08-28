//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/**
 * Ports `RadioFranceProvider`. This API tells the client how long to wait before polling again
 * (`delayToRefresh`), so instead of a fixed [pollInterval] the provider reschedules itself — the
 * reschedule is fired off independently of parsing the *current* response, matching the iOS
 * version's fire-and-forget `DispatchQueue.main.asyncAfter` (WidgetView.swift:739-753).
 */
class RadioFranceProvider : MetadataProvider {
    override val pollInterval: Long? = null

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { fetchAndScheduleNext(apiUrl, onResult) }
    }

    override fun cancel() {
        scope.cancel()
    }

    private suspend fun fetchAndScheduleNext(apiUrl: String, onResult: (MetadataResult) -> Unit) {
        val json = fetchJson(apiUrl) ?: return

        if (json.has("delayToRefresh")) {
            val intervalSeconds = maxOf(json.optDouble("delayToRefresh") / 1000.0, 10.0)
            scope.launch {
                delay((intervalSeconds * 1000).toLong())
                fetchAndScheduleNext(apiUrl, onResult)
            }
        }

        val nowBlock = json.optJSONObject("now")
        val nextArray = json.optJSONArray("next")
        val nextBlock = if (nextArray != null && nextArray.length() > 0) {
            nextArray.optJSONObject(0)
        } else {
            null
        }
        val block = when {
            nowBlock?.opt("favoriteUuid") is String -> nowBlock
            nextBlock?.opt("favoriteUuid") is String -> nextBlock
            else -> nowBlock
        } ?: return

        val firstLine = block.optString("firstLine").trim()
        val secondLine = block.optString("secondLine").trim()
        val parts = secondLine.split(" • ")

        val artist: String
        val title: String
        if (parts.size >= 2) {
            artist = MetadataTextUtils.cleanMetadataString(parts[0].trim())
            title = MetadataTextUtils.cleanMetadataString(parts.drop(1).joinToString(" • ").trim())
        } else {
            artist = ""
            title = MetadataTextUtils.cleanMetadataString(firstLine)
        }

        if (title.isEmpty() || MetadataTextUtils.isJunkMetadata(title)) return
        onResult(MetadataResult(artist, title))
    }

    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            NetworkClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                JSONObject(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val STREAM_TO_API: Map<String, String> = mapOf(
            "icecast.radiofrance.fr/fb100pour100annees80" to
                "https://api.radiofrance.fr/livemeta/live/5602/transistor_musical_player",
            "icecast.radiofrance.fr/fbchansonfrancaise" to
                "https://api.radiofrance.fr/livemeta/live/5601/transistor_musical_player",
            "icecast.radiofrance.fr/fip-hifi" to
                "https://api.radiofrance.fr/livemeta/live/7/transistor_musical_player",
            "icecast.radiofrance.fr/fip-midfi" to
                "https://api.radiofrance.fr/livemeta/live/7/transistor_musical_player",
            "icecast.radiofrance.fr/fipcultes" to
                "https://api.radiofrance.fr/livemeta/live/709/transistor_musical_player",
            "icecast.radiofrance.fr/fipelectro" to
                "https://api.radiofrance.fr/livemeta/live/74/transistor_musical_player",
            "icecast.radiofrance.fr/fipgroove" to
                "https://api.radiofrance.fr/livemeta/live/66/transistor_musical_player",
            "icecast.radiofrance.fr/fiphiphop" to
                "https://api.radiofrance.fr/livemeta/live/95/transistor_musical_player",
            "icecast.radiofrance.fr/fipjazz" to
                "https://api.radiofrance.fr/livemeta/live/65/transistor_musical_player",
            "icecast.radiofrance.fr/fipmetal" to
                "https://api.radiofrance.fr/livemeta/live/77/transistor_musical_player",
            "icecast.radiofrance.fr/fipmonde" to
                "https://api.radiofrance.fr/livemeta/live/69/transistor_musical_player",
            "icecast.radiofrance.fr/fipnouveautes" to
                "https://api.radiofrance.fr/livemeta/live/70/transistor_musical_player",
            "icecast.radiofrance.fr/fippop" to
                "https://api.radiofrance.fr/livemeta/live/78/transistor_musical_player",
            "icecast.radiofrance.fr/fipreggae" to
                "https://api.radiofrance.fr/livemeta/live/71/transistor_musical_player",
            "icecast.radiofrance.fr/fiprock" to
                "https://api.radiofrance.fr/livemeta/live/64/transistor_musical_player",
            "icecast.radiofrance.fr/fipsacrefrancais" to
                "https://api.radiofrance.fr/livemeta/live/96/transistor_musical_player",
            "icecast.radiofrance.fr/franceinter-hifi" to
                "https://api.radiofrance.fr/livemeta/live/1/transistor_inter_player",
            "icecast.radiofrance.fr/franceinter-midfi" to
                "https://api.radiofrance.fr/livemeta/live/1/transistor_inter_player",
            "icecast.radiofrance.fr/franceinterlamusiqueinter" to
                "https://api.radiofrance.fr/livemeta/live/1101/transistor_musical_player",
            "icecast.radiofrance.fr/francemusique-hifi" to
                "https://api.radiofrance.fr/livemeta/live/4/transistor_musique_player",
            "icecast.radiofrance.fr/francemusique-midfi" to
                "https://api.radiofrance.fr/livemeta/live/4/transistor_musique_player",
            "icecast.radiofrance.fr/francemusiquebaroque" to
                "https://api.radiofrance.fr/livemeta/live/408/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiqueclassiquelove" to
                "https://api.radiofrance.fr/livemeta/live/411/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiqueclassiqueplus" to
                "https://api.radiofrance.fr/livemeta/live/402/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiqueconcertsradiofrance" to
                "https://api.radiofrance.fr/livemeta/live/403/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiqueeasyclassique" to
                "https://api.radiofrance.fr/livemeta/live/401/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiquelajazz" to
                "https://api.radiofrance.fr/livemeta/live/407/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiquelacontemporaine" to
                "https://api.radiofrance.fr/livemeta/live/406/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiquelalabo" to
                "https://api.radiofrance.fr/livemeta/live/405/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiquecoramonde" to
                "https://api.radiofrance.fr/livemeta/live/404/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiqueOpera" to
                "https://api.radiofrance.fr/livemeta/live/409/transistor_musical_player",
            "icecast.radiofrance.fr/francemusiquepianozen" to
                "https://api.radiofrance.fr/livemeta/live/410/transistor_musical_player",
            "icecast.radiofrance.fr/mouv-hifi" to
                "https://api.radiofrance.fr/livemeta/live/6/transistor_mouv_player",
            "icecast.radiofrance.fr/mouv-midfi" to
                "https://api.radiofrance.fr/livemeta/live/6/transistor_mouv_player",
        )
    }
}
