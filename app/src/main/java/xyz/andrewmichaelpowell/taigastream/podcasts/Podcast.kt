//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.podcasts

import java.util.UUID
import org.json.JSONObject

data class Podcast(
    val id: UUID = UUID.randomUUID(),
    val feedUrl: String,
    val name: String,
    val artworkUrl: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id.toString())
        .put("feedUrl", feedUrl)
        .put("name", name)
        .put("artworkUrl", artworkUrl)

    companion object {
        val EMPTY = Podcast(feedUrl = "", name = "", artworkUrl = "")

        fun fromJson(json: JSONObject): Podcast? {
            val feedUrl = json.optString("feedUrl")
            if (feedUrl.isEmpty()) return null
            return Podcast(
                id = runCatching { UUID.fromString(json.optString("id")) }.getOrElse { UUID.randomUUID() },
                feedUrl = feedUrl,
                name = json.optString("name"),
                artworkUrl = json.optString("artworkUrl"),
            )
        }
    }
}

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    /** Milliseconds since the epoch. */
    val publishedAt: Long?,
    /** Seconds. */
    val duration: Double?,
    val artworkUrl: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("audioUrl", audioUrl)
        .apply {
            publishedAt?.let { put("publishedAt", it) }
            duration?.let { put("duration", it) }
            artworkUrl?.let { put("artworkUrl", it) }
        }

    companion object {
        fun fromJson(json: JSONObject): PodcastEpisode? {
            val audioUrl = json.optString("audioUrl")
            if (audioUrl.isEmpty()) return null
            return PodcastEpisode(
                id = json.optString("id").ifEmpty { audioUrl },
                title = json.optString("title"),
                audioUrl = audioUrl,
                publishedAt = if (json.has("publishedAt")) json.optLong("publishedAt") else null,
                duration = if (json.has("duration")) json.optDouble("duration") else null,
                artworkUrl = json.optString("artworkUrl").ifEmpty { null },
            )
        }
    }
}

/**
 * Where the listener is in a podcast: the episode they're on, how far in,
 * and whether they picked it by hand.
 */
data class EpisodeState(
    val episode: PodcastEpisode,
    val position: Double,
    val isPinned: Boolean,
    val updatedAt: Long,
) {
    /** Partway through: past the first few seconds. */
    val isInProgress: Boolean get() = position >= 5

    fun toJson(): JSONObject = JSONObject()
        .put("episode", episode.toJson())
        .put("position", position)
        .put("isPinned", isPinned)
        .put("updatedAt", updatedAt)

    companion object {
        /** Positions this close to the end count as finished. */
        const val FINISHED_THRESHOLD = 30.0

        fun fromJson(json: JSONObject): EpisodeState? {
            val episode = json.optJSONObject("episode")?.let { PodcastEpisode.fromJson(it) } ?: return null
            return EpisodeState(
                episode = episode,
                position = json.optDouble("position", 0.0),
                isPinned = json.optBoolean("isPinned"),
                updatedAt = json.optLong("updatedAt"),
            )
        }
    }
}

data class PodcastFeed(
    val title: String,
    val artworkUrl: String,
    /** Newest first. */
    val episodes: List<PodcastEpisode>,
)
