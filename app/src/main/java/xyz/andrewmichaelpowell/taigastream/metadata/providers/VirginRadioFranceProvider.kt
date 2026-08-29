//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import java.io.IOException
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

class VirginRadioFranceProvider : MetadataProvider {
    override val pollInterval: Long = 15

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return STREAM_TO_API.keys.any { s.contains(it) }
    }

    private fun apiEntry(streamUrl: HttpUrl): Pair<String, String>? {
        val s = streamUrl.toString()
        return STREAM_TO_API.entries.firstOrNull { s.contains(it.key) }?.value
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val (apiUrl, key) = apiEntry(streamUrl) ?: return
        val session = NetworkClient.client.newBuilder().cookieJar(InMemoryCookieJar()).build()

        session.newCall(NetworkClient.get("https://virginradio.fr")).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = fetchOnAir(session, apiUrl, key, onResult)
            override fun onResponse(call: Call, response: Response) {
                response.close()
                fetchOnAir(session, apiUrl, key, onResult)
            }
        })
    }

    private fun fetchOnAir(
        session: okhttp3.OkHttpClient,
        apiUrl: String,
        key: String,
        onResult: (MetadataResult) -> Unit,
    ) {
        val requestBody = JSONObject().put("radio_ids", JSONArray().put(key)).toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .header("Referer", "https://virginradio.fr/")
            .header("Origin", "https://virginradio.fr")
            .header(
                "User-Agent",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            )
            .build()

        session.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) return
                    val bodyText = resp.body.string()
                    val json = runCatching { JSONObject(bodyText) }.getOrNull() ?: return
                    val station = json.optJSONObject(key) ?: return
                    val artist = station.optString("artist").trim()
                    val title = station.optString("title").trim()
                    if (title.isEmpty()) return

                    val cover = station.optString("cover")
                    val artworkUrl = if (cover.isNotEmpty() && !cover.startsWith("/")) cover else null
                    onResult(MetadataResult(artist, title, artworkUrl))
                }
            }
        })
    }

    private class InMemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    }

    companion object {
        private val STREAM_TO_API: Map<String, Pair<String, String>> = mapOf(
            "virginradio.ice.infomaniak.ch" to ("https://virginradio.fr/lite/update_onair" to "1"),
            "virginclassiquerock.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "5"),
            "virginrocklive.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "4"),
            "virginrockfrancais.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "8"),
            "virginlegendesdurock.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "7"),
            "virginmetal.ice.infomaniak.ch" to ("https://virginradio.fr/lite/update_onair" to "9"),
            "virginrockannees60.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "15"),
            "virginrockannees70.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "16"),
            "virginrockannees80.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "10"),
            "virginrockannees90.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "6"),
            "virginrockannees2000.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "3"),
            "virginrockamericain.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "11"),
            "virginnoveauxtalents.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "14"),
            "virginrockparty.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "13"),
            "virginrockanglais.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "12"),
            "virginradiorockballad.ice.infomaniak.ch" to
                ("https://virginradio.fr/lite/update_onair" to "19"),
        )
    }
}
