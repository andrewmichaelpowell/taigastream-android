//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.search

import java.io.IOException
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

data class RadioBrowserStation(
    val id: String,
    val name: String,
    val url: String,
    val faviconUrl: String,
    val country: String,
    val state: String,
    val language: String,
    val tags: String,
    val votes: Int,
    val bitrate: Int,
)

/**
 * Ports `RadioBrowserClient` (MainView.swift:116-263), including its mirror-server load-balancing
 * trick: `all.api.radio-browser.info` resolves to every active mirror's IP, and reverse-DNS on
 * each address recovers that mirror's real hostname (`CFHost`/`getnameinfo` on iOS;
 * `InetAddress`'s forward + reverse lookups here) so requests spread across servers instead of
 * hammering one.
 */
class RadioBrowserClient private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var baseUrl = "https://de1.api.radio-browser.info"
    @Volatile private var serverResolved = false

    init {
        resolveServer()
    }

    private fun resolveServer() {
        scope.launch {
            try {
                val addresses = InetAddress.getAllByName("all.api.radio-browser.info")
                val hostnames = addresses
                    .map { it.canonicalHostName }
                    .filterIndexed { index, hostname -> hostname != addresses[index].hostAddress }
                    .distinct()
                if (hostnames.isNotEmpty()) {
                    baseUrl = "https://${hostnames.random()}"
                    serverResolved = true
                }
            } catch (e: Exception) {
                // Keep the default baseUrl.
            }
        }
    }

    data class SearchParams(
        val name: String = "",
        val limit: Int = 50,
        val offset: Int = 0,
        val order: String = "votes",
        val reverse: Boolean = true,
        val hideBroken: Boolean = true,
    )

    fun search(params: SearchParams, onResult: (List<RadioBrowserStation>) -> Unit) {
        if (!serverResolved) {
            scope.launch {
                delay(1000)
                search(params, onResult)
            }
            return
        }

        val urlBuilder = "$baseUrl/json/stations/search".toHttpUrl().newBuilder()
            .addQueryParameter("limit", params.limit.toString())
            .addQueryParameter("offset", params.offset.toString())
            .addQueryParameter("order", params.order)
            .addQueryParameter("reverse", params.reverse.toString())
            .addQueryParameter("hidebroken", params.hideBroken.toString())
        if (params.name.isNotEmpty()) urlBuilder.addQueryParameter("name", params.name)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "TaigaStream/1.0")
            .build()

        NetworkClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onResult(emptyList())

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = if (resp.isSuccessful) resp.body.string() else null
                    val json = body?.let { runCatching { JSONArray(it) }.getOrNull() }
                    if (json == null) {
                        onResult(emptyList())
                        return
                    }
                    val stations = (0 until json.length()).mapNotNull { i ->
                        val dict = json.optJSONObject(i) ?: return@mapNotNull null
                        val url = dict.optString("url_resolved").ifEmpty { dict.optString("url") }
                        if (url.isEmpty()) return@mapNotNull null
                        RadioBrowserStation(
                            id = dict.optString("stationuuid").ifEmpty { UUID.randomUUID().toString() },
                            name = dict.optString("name"),
                            url = url,
                            faviconUrl = dict.optString("favicon"),
                            country = dict.optString("country"),
                            state = dict.optString("state"),
                            language = dict.optString("language"),
                            tags = dict.optString("tags"),
                            votes = dict.optInt("votes"),
                            bitrate = dict.optInt("bitrate"),
                        )
                    }
                    onResult(stations)
                }
            }
        })
    }

    fun recordClick(stationId: String) {
        val request = Request.Builder()
            .url("$baseUrl/json/url/$stationId")
            .header("User-Agent", "TaigaStream/1.0")
            .build()
        NetworkClient.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    companion object {
        val shared: RadioBrowserClient by lazy { RadioBrowserClient() }
    }
}
