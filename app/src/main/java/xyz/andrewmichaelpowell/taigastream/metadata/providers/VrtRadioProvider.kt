//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class VrtRadioProvider : MetadataProvider {
    override val pollInterval: Long = 30

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return SORTED_KEYS.any { s.contains(it) }
    }

    private fun pagePath(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString()
        val key = SORTED_KEYS.firstOrNull { s.contains(it) } ?: return null
        return STREAM_TO_PAGE[key]
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val pagePath = pagePath(streamUrl) ?: return
        val bodyJson = JSONObject()
            .put("query", GRAPHQL_QUERY)
            .put("variables", JSONObject().put("path", pagePath))
        val requestBody = bodyJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://www.vrt.be/vrtnu-api/graphql/public/v1")
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Origin", "https://www.vrt.be")
            .header("Referer", "https://www.vrt.be$pagePath")
            .header("x-vrt-client-name", "WEB")
            .header("x-vrt-client-version", "1.5.17")
            .header("x-vrt-zone", "default")
            .header(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            )
            .build()

        NetworkClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) return
                    val bodyText = resp.body.string()
                    val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: return
                    val player = json.optJSONObject("data")
                        ?.optJSONObject("page")
                        ?.optJSONObject("player")
                        ?: return

                    val title = player.optString("title").trim()
                    val artist = player.optString("subtitle").trim()
                    if (title.isEmpty() || MetadataTextUtils.isJunkMetadata(title)) return
                    onResult(MetadataResult(artist, title))
                }
            }
        })
    }

    companion object {
        private const val GRAPHQL_QUERY =
            "query AudioLivestreamPage(\$path: ID!) { page(id: \$path) { ... on AudioLivestreamPage " +
                "{ player { title subtitle maxAge } } } }"

        private val STREAM_TO_PAGE: Map<String, String> = mapOf(
            "icecast.vrtcdn.be/radio1" to "/vrtmax/livestream/audio/radio1/",
            "icecast.vrtcdn.be/ra2ant" to "/vrtmax/livestream/audio/radio2-antwerpen/",
            "icecast.vrtcdn.be/ra2lim" to "/vrtmax/livestream/audio/radio2-limburg/",
            "icecast.vrtcdn.be/ra2ovl" to "/vrtmax/livestream/audio/radio2-oost-vlaanderen/",
            "icecast.vrtcdn.be/ra2vlb" to "/vrtmax/livestream/audio/radio2-vlaams-brabant/",
            "icecast.vrtcdn.be/ra2wvl" to "/vrtmax/livestream/audio/radio2-west-vlaanderen/",
            "icecast.vrtcdn.be/klara" to "/vrtmax/livestream/audio/klara/",
            "icecast.vrtcdn.be/klaracontinuo" to "/vrtmax/livestream/audio/klara-continuo/",
            "icecast.vrtcdn.be/stubru" to "/vrtmax/livestream/audio/stubru/",
            "icecast.vrtcdn.be/mnm" to "/vrtmax/livestream/audio/mnm/",
            "icecast.vrtcdn.be/mnm_hits" to "/vrtmax/livestream/audio/mnm-hits/",
        )

        private val SORTED_KEYS = STREAM_TO_PAGE.keys.sortedByDescending { it.length }
    }
}
