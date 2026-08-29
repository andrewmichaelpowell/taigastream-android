//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.URLEncoder
import org.json.JSONObject

object ArtworkFetcher {

    fun resolve(
        artist: String,
        title: String,
        artworkUrl: String?,
        onFailure: () -> Unit = {},
        onBitmap: (Bitmap) -> Unit,
    ) {
        if (!artworkUrl.isNullOrEmpty()) {
            downloadBitmap(
                artworkUrl,
                onBitmap,
                onFailure = { searchItunes(artist, title, onFailure, onBitmap) },
            )
            return
        }
        searchItunes(artist, title, onFailure, onBitmap)
    }

    private fun searchItunes(
        artist: String,
        title: String,
        onFailure: () -> Unit,
        onBitmap: (Bitmap) -> Unit,
    ) {
        val term = URLEncoder.encode("$artist $title", "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=song&limit=1"
        NetworkClient.fetchBody(NetworkClient.get(url), onFailure = onFailure) { body ->
            val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
            if (results == null || results.length() == 0) return@fetchBody onFailure()
            val artworkUrlSmall = results.getJSONObject(0).optString("artworkUrl100")
            if (artworkUrlSmall.isEmpty()) return@fetchBody onFailure()
            val highRes = artworkUrlSmall.replace("100x100bb", "600x600bb")
            downloadBitmap(highRes, onBitmap, onFailure)
        }
    }

    private fun downloadBitmap(url: String, onBitmap: (Bitmap) -> Unit, onFailure: () -> Unit = {}) {
        NetworkClient.fetchBytes(NetworkClient.get(url), onFailure = onFailure) { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) onBitmap(bitmap) else onFailure()
        }
    }
}
