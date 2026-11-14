//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.podcasts

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient
import kotlin.coroutines.resume

data class PodcastSearchResult(
    val id: Long,
    val name: String,
    val author: String,
    val genre: String,
    val feedUrl: String,
    val artworkUrl: String,
)

/** Finds podcasts by name with the iTunes Search API. */
object PodcastSearchClient {
    suspend fun search(term: String): List<PodcastSearchResult> {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "50")
            .addQueryParameter("term", term)
            .build()
            .toString()
        val body: String = suspendCancellableCoroutine { continuation ->
            NetworkClient.fetchBody(
                NetworkClient.get(url),
                onFailure = { if (continuation.isActive) continuation.resume(null) },
            ) { if (continuation.isActive) continuation.resume(it) }
        } ?: return emptyList()
        return runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val feedUrl = item.optString("feedUrl")
                if (feedUrl.isEmpty()) return@mapNotNull null
                PodcastSearchResult(
                    id = item.optLong("collectionId"),
                    name = item.optString("collectionName"),
                    author = item.optString("artistName"),
                    genre = item.optString("primaryGenreName"),
                    feedUrl = feedUrl,
                    artworkUrl = item.optString("artworkUrl600").ifEmpty { item.optString("artworkUrl100") },
                )
            }.distinctBy { it.feedUrl }
        }.getOrDefault(emptyList())
    }
}
