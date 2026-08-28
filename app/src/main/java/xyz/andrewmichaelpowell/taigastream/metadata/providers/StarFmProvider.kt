//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata.providers

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataTextUtils
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient

/** Ports `StarFMProvider` — a live WebSocket feed per station (Star FM / Regenbogen2 network). */
class StarFmProvider : MetadataProvider {
    override val pollInterval: Long? = null

    private var socket: WebSocket? = null

    override fun matches(streamUrl: HttpUrl): Boolean {
        val s = streamUrl.toString()
        return STREAM_TO_WEBSOCKET.keys.any { s.contains(it) }
    }

    private fun wsUrl(streamUrl: HttpUrl): String? {
        val s = streamUrl.toString()
        return STREAM_TO_WEBSOCKET.entries.firstOrNull { s.contains(it.key) }?.value
    }

    override fun poll(streamUrl: HttpUrl, onResult: (MetadataResult) -> Unit) {
        val wsUrl = wsUrl(streamUrl) ?: return
        val request = Request.Builder().url(wsUrl).build()
        socket = NetworkClient.client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                val artist = json.optString("artist").trim()
                val title = json.optString("song").trim()
                if (title.isNotEmpty() && !MetadataTextUtils.isJunkMetadata(title)) {
                    onResult(MetadataResult(artist, title))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
            }
        })
    }

    override fun cancel() {
        socket?.cancel()
        socket = null
    }

    companion object {
        private val STREAM_TO_WEBSOCKET: Map<String, String> = mapOf(
            "stream.starfm.de/berlin" to "wss://api.streamabc.net/metadata/channel/30_vqtea82nbeon_wvxg",
            "stream.starfm.de/nbg" to "wss://api.streamabc.net/metadata/channel/30_nbw9xzg7b53v_rgfj",
            "stream.starfm.de/sachsen" to "wss://api.streamabc.net/metadata/channel/30_ngfg2edxug0a_dtze",
            "stream.starfm.de/nrw" to "wss://api.streamabc.net/metadata/channel/30_7hciprr0pewh_dkmc",
            "stream.starfm.de/national" to "wss://api.streamabc.net/metadata/channel/30_2d7qgd0rqsqd_w0dh",
            "stream.starfm.de/alternat" to "wss://api.streamabc.net/metadata/channel/30_9cjjuqbztc7b_w6tv",
            "stream.starfm.de/90srock" to "wss://api.streamabc.net/metadata/channel/30_eoplhpklkmnv_zqnc",
            "stream.regenbogen2.de/festivalradio" to "wss://api.streamabc.net/metadata/channel/atsw_tit9bqllti_g79y",
            "stream.starfm.de/newmetal" to "wss://api.streamabc.net/metadata/channel/30_nz784lvvrv58_unsh",
            "stream.starfm.de/hardrock" to "wss://api.streamabc.net/metadata/channel/30_x7lg1zfcn9df_3swf",
            "stream.starfm.de/fromhell" to "wss://api.streamabc.net/metadata/channel/30_lpuzm574hotr_d953",
            "stream.starfm.de/80srock" to "wss://api.streamabc.net/metadata/channel/30_lu3nhavsoefx_avs0",
            "stream.starfm.de/classic" to "wss://api.streamabc.net/metadata/channel/30_wiqder3bbvvp_amxb",
            "stream.starfm.de/blues" to "wss://api.streamabc.net/metadata/channel/30_yn083yo8fisj_ccoq",
            "stream.starfm.de/ballads" to "wss://api.streamabc.net/metadata/channel/30_7fh19amhhhoe_d25l",
            "stream.starfm.de/country" to "wss://api.streamabc.net/metadata/channel/30_euch8c5krctp_dirs",
            "stream.starfm.de/xmas" to "wss://api.streamabc.net/metadata/channel/30_1x9rkasxzg6m_isgi",
            "stream.starfm.de/newrock" to "wss://api.streamabc.net/metadata/channel/30_etgneoupeuu8_vu9s",
            "stream.starfm.de/bbrock" to "wss://api.streamabc.net/metadata/channel/30_vchltty8vm2i_1gq3",
        )
    }
}
