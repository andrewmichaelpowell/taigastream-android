//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class AbcRadioProvider : MetadataProvider {
    override val pollInterval: Long = 15

    private fun stationCode(streamUrl: HttpUrl): String? {
        val host = streamUrl.host
        val segments = streamUrl.pathSegments
        return if (host.contains("akamaized.net")) {
            val liveIndex = segments.indexOf("live")
            if (liveIndex >= 0 && segments.size > liveIndex + 2) segments[liveIndex + 2] else null
        } else {
            segments.lastOrNull { it.isNotEmpty() }?.substringBefore(".")
        }
    }

    override fun matches(streamUrl: HttpUrl): Boolean {
        val host = streamUrl.host
        if (SLUG_HOSTS.any { host.contains(it) }) {
            val code = stationCode(streamUrl) ?: return false
            return STATION_CODE_TO_API.containsKey(code)
        }
        val s = streamUrl.toString()
        return SORTED_KEYS.any { s.contains(it) }
    }

    private fun apiUrl(streamUrl: HttpUrl): String? {
        val host = streamUrl.host
        if (SLUG_HOSTS.any { host.contains(it) }) {
            val code = stationCode(streamUrl) ?: return null
            return STATION_CODE_TO_API[code]
        }
        val s = streamUrl.toString()
        val key = SORTED_KEYS.firstOrNull { s.contains(it) } ?: return null
        return STREAM_TO_API[key]
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val apiUrl = apiUrl(streamUrl) ?: return
        NetworkClient.fetchBody(NetworkClient.get(apiUrl)) { body ->
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@fetchBody
            val mapiNow = json.optJSONObject("mapiNow") ?: return@fetchBody
            val title = mapiNow.optString("title").trim()
            val artist = mapiNow.optString("artist").trim()
            if (title.isEmpty()) return@fetchBody
            val artworkUrl = mapiNow.optString("primaryImage").ifEmpty { null }
            onResult(MetadataResult(artist, title, artworkUrl))
        }
    }

    companion object {
        private val STREAM_TO_API: Map<String, String> = run {
            val base = "https://www.abc.net.au/core-next/api/musicNowPlaying"
            mapOf(
                "mediahubaustralia.com/CTRW" to "$base/COUNTRY?tz=Australia%2FSydney",
                "mediahubaustralia.com/JAZW" to "$base/JAZZ?tz=Australia%2FSydney",
                "mediahubaustralia.com/2FMW" to "$base/CLASSIC?tz=Australia%2FSydney",
                "mediahubaustralia.com/3FMW" to "$base/CLASSIC?tz=Australia%2FSydney",
                "mediahubaustralia.com/4FMW" to "$base/CLASSIC?tz=Australia%2FBrisbane",
                "mediahubaustralia.com/5FMW" to "$base/CLASSIC?tz=Australia%2FAdelaide",
                "mediahubaustralia.com/6FMW" to "$base/CLASSIC?tz=Australia%2FPerth",
                "mediahubaustralia.com/8FMW" to "$base/CLASSIC?tz=Australia%2FDarwin",
                "mediahubaustralia.com/FM2W" to "$base/CLASSIC2?tz=Australia%2FSydney",
                "mediahubaustralia.com/DJDW" to "$base/DOUBLEJ?tz=Australia%2FSydney",
                "mediahubaustralia.com/3DJW" to "$base/DOUBLEJ?tz=Australia%2FSydney",
                "mediahubaustralia.com/4DJW" to "$base/DOUBLEJ?tz=Australia%2FBrisbane",
                "mediahubaustralia.com/5DJW" to "$base/DOUBLEJ?tz=Australia%2FAdelaide",
                "mediahubaustralia.com/6DJW" to "$base/DOUBLEJ?tz=Australia%2FPerth",
                "mediahubaustralia.com/8DJW" to "$base/DOUBLEJ?tz=Australia%2FDarwin",
                "mediahubaustralia.com/2TJW" to "$base/TRIPLEJ?tz=Australia%2FSydney",
                "mediahubaustralia.com/3TJW" to "$base/TRIPLEJ?tz=Australia%2FSydney",
                "mediahubaustralia.com/4TJW" to "$base/TRIPLEJ?tz=Australia%2FBrisbane",
                "mediahubaustralia.com/5TJW" to "$base/TRIPLEJ?tz=Australia%2FAdelaide",
                "mediahubaustralia.com/6TJW" to "$base/TRIPLEJ?tz=Australia%2FPerth",
                "mediahubaustralia.com/8TJW" to "$base/TRIPLEJ?tz=Australia%2FDarwin",
                "mediahubaustralia.com/TJHW" to "$base/H100?tz=Australia%2FSydney",
                "mediahubaustralia.com/UNEW" to "$base/UNEARTHED?tz=Australia%2FSydney",
            )
        }

        private val STATION_CODE_TO_API: Map<String, String> = run {
            val base = "https://www.abc.net.au/core-next/api/musicNowPlaying"
            mapOf(
                "abccountry" to "$base/COUNTRY?tz=Australia%2FSydney",
                "abcjazz" to "$base/JAZZ?tz=Australia%2FSydney",
                "classicfmnsw" to "$base/CLASSIC?tz=Australia%2FSydney",
                "classicfmnt" to "$base/CLASSIC?tz=Australia%2FDarwin",
                "classicfmqld" to "$base/CLASSIC?tz=Australia%2FBrisbane",
                "classicfmsa" to "$base/CLASSIC?tz=Australia%2FAdelaide",
                "classicfmvic" to "$base/CLASSIC?tz=Australia%2FSydney",
                "classicfmwa" to "$base/CLASSIC?tz=Australia%2FPerth",
                "classic2" to "$base/CLASSIC2?tz=Australia%2FSydney",
                "doublejnsw" to "$base/DOUBLEJ?tz=Australia%2FSydney",
                "doublejnt" to "$base/DOUBLEJ?tz=Australia%2FDarwin",
                "doublejqld" to "$base/DOUBLEJ?tz=Australia%2FBrisbane",
                "doublejsa" to "$base/DOUBLEJ?tz=Australia%2FAdelaide",
                "doublejvic" to "$base/DOUBLEJ?tz=Australia%2FSydney",
                "doublejwa" to "$base/DOUBLEJ?tz=Australia%2FPerth",
                "triplejnsw" to "$base/TRIPLEJ?tz=Australia%2FSydney",
                "triplejnt" to "$base/TRIPLEJ?tz=Australia%2FDarwin",
                "triplejqld" to "$base/TRIPLEJ?tz=Australia%2FBrisbane",
                "triplejsa" to "$base/TRIPLEJ?tz=Australia%2FAdelaide",
                "triplejvic" to "$base/TRIPLEJ?tz=Australia%2FSydney",
                "triplejwa" to "$base/TRIPLEJ?tz=Australia%2FPerth",
                "triplejhottest" to "$base/H100?tz=Australia%2FSydney",
                "triplejunearthed" to "$base/UNEARTHED?tz=Australia%2FSydney",
            )
        }

        private val SORTED_KEYS = STREAM_TO_API.keys.sortedByDescending { it.length }

        private val SLUG_HOSTS =
            listOf("akamaized.net", "streamguys1.com", "streaming.abc-cdn.net.au")
    }
}
