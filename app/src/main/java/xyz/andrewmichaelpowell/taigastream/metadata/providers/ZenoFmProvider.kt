//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/**
 * Ports `ZenoFMProvider` — a long-lived server-sent-events style subscription
 * (Taiga Stream Widget/WidgetView.swift:1372-1498). The iOS version also declares a 30s
 * [pollInterval], which causes `StreamInfo` to open an *additional* overlapping SSE connection
 * every 30 seconds on top of the one already streaming (WidgetView.swift:2283-2288) — a stray
 * connection leak rather than intentional behavior. Here [pollInterval] is `null`: one persistent
 * connection is opened and explicitly torn down via [cancel] when the stream changes.
 */
class ZenoFmProvider : MetadataProvider {
    override val pollInterval: Long? = null

    private var activeCall: Call? = null

    override fun matches(streamUrl: HttpUrl): Boolean = streamUrl.host.contains("stream.zeno.fm")

    private fun mountId(streamUrl: HttpUrl): String? =
        streamUrl.pathSegments.firstOrNull { it.isNotEmpty() }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val mount = mountId(streamUrl) ?: return
        val apiUrl = "https://api.zeno.fm/mounts/metadata/subscribe/$mount"

        val streamingClient = NetworkClient.client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val call = streamingClient.newCall(request)
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) return
                    val source = resp.body.source()
                    val eventLines = StringBuilder()
                    try {
                        while (!call.isCanceled()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isEmpty()) {
                                processEvent(eventLines.toString(), onResult)
                                eventLines.setLength(0)
                            } else {
                                eventLines.append(line).append('\n')
                            }
                        }
                    } catch (e: IOException) {
                        // Connection closed or cancelled; nothing to clean up.
                    }
                }
            }
        })
    }

    override fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    private fun processEvent(event: String, onResult: (MetadataResult) -> Unit) {
        for (line in event.split("\n")) {
            if (!line.startsWith("data: ")) continue
            val json = runCatching { JSONObject(line.removePrefix("data: ")) }.getOrNull() ?: continue

            var streamTitle = json.optString("streamTitle").trim()
            if (streamTitle.isEmpty()) continue
            while (streamTitle.contains("  ")) streamTitle = streamTitle.replace("  ", " ")

            var artist = ""
            var title = streamTitle

            if (streamTitle.contains(" - ")) {
                val (parsedArtist, parsedTitle) = MetadataTextUtils.splitArtistTitle(streamTitle)
                if (parsedArtist.isNotEmpty()) {
                    artist = parsedArtist
                    title = parsedTitle
                }
            } else if (streamTitle.contains("-")) {
                val parts = streamTitle.split("-").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val parsedArtist = MetadataTextUtils.cleanMetadataString(parts[0])
                    val parsedTitle = MetadataTextUtils.cleanMetadataString(parts[1])
                    if (parsedArtist.isNotEmpty() && parsedTitle.isNotEmpty()) {
                        artist = parsedArtist
                        title = parsedTitle
                    }
                }
            }

            if (title.isEmpty() || MetadataTextUtils.isJunkMetadata(title)) continue
            onResult(MetadataResult(artist, title))
        }
    }
}
