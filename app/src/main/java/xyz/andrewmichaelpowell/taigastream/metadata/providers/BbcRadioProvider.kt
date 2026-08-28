//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `BBCRadioProvider`. */
class BbcRadioProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return SORTED_KEYS.any { s.contains(it) }
    }

    private fun apiUrl(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString()
        val key = SORTED_KEYS.firstOrNull { s.contains(it) } ?: return null
        return SERVICE_MAP[key]
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl = apiUrl(streamUrl) ?: return
        NetworkClient.fetchBody(
            NetworkClient.get(apiUrl, headers = mapOf("Accept" to "application/json"))
        ) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val dataArray = json.optJSONArray("data") ?: return@fetchBody
            var nowPlaying: JSONObject? = null
            for (i in 0 until dataArray.length()) {
                val entry = dataArray.optJSONObject(i) ?: continue
                if (entry.optJSONObject("offset")?.optBoolean("now_playing") == true) {
                    nowPlaying = entry
                    break
                }
            }
            if (nowPlaying == null && dataArray.length() > 0) nowPlaying = dataArray.optJSONObject(0)
            val titles = nowPlaying?.optJSONObject("titles") ?: return@fetchBody
            val artist = titles.optString("primary").trim()
            val title = titles.optString("secondary").trim()
            onResult(MetadataResult(artist, title))
        }
    }

    companion object {
        private val SERVICE_MAP: Map<String, String> = run {
            val base = "https://rms.api.bbc.co.uk/v2/services"
            val suffix = "segments/latest?experience=domestic&offset=0&limit=1"
            listOf(
                "bbc_1xtra", "bbc_6music", "bbc_radio_one", "bbc_radio_one_anthems",
                "bbc_radio_one_dance", "bbc_radio_two", "bbc_radio_three", "bbc_radio_three_unwind",
                "bbc_radio_four_extra", "bbc_radio_five_live", "bbc_asian_network",
                "bbc_radio_scotland", "bbc_radio_scotland_mw", "bbc_radio_orkney",
                "bbc_radio_shetland", "bbc_radio_nan_gaidheal", "bbc_radio_wales",
                "bbc_radio_wales_am", "bbc_radio_ulster", "bbc_radio_cymru", "bbc_radio_cymru_2",
                "bbc_radio_foyle", "bbc_radio_berkshire", "bbc_radio_bristol",
                "bbc_radio_cambridge", "bbc_radio_cornwall", "bbc_radio_coventry_warwickshire",
                "bbc_radio_cumbria", "bbc_radio_derby", "bbc_radio_devon", "bbc_radio_essex",
                "bbc_radio_gloucestershire", "bbc_radio_guernsey", "bbc_radio_hereford_worcester",
                "bbc_radio_humberside", "bbc_radio_jersey", "bbc_radio_kent",
                "bbc_radio_lancashire", "bbc_radio_leeds", "bbc_radio_leicester",
                "bbc_radio_lincolnshire", "bbc_london", "bbc_radio_manchester",
                "bbc_radio_merseyside", "bbc_radio_newcastle", "bbc_radio_norfolk",
                "bbc_radio_northampton", "bbc_radio_nottingham", "bbc_radio_oxford",
                "bbc_radio_sheffield", "bbc_radio_shropshire", "bbc_radio_solent",
                "bbc_radio_west_dorset", "bbc_radio_somerset_sound", "bbc_radio_stoke",
                "bbc_radio_suffolk", "bbc_radio_surrey", "bbc_radio_sussex", "bbc_tees",
                "bbc_three_counties_radio", "bbc_radio_wiltshire", "bbc_wm", "bbc_radio_york",
                "bbc_world_service",
            ).associateWith { "$base/$it/$suffix" } +
                mapOf("bbc_radio_solent_west_dorset" to "$base/bbc_radio_west_dorset/$suffix")
        }

        private val SORTED_KEYS = SERVICE_MAP.keys.sortedByDescending { it.length }
    }
}
