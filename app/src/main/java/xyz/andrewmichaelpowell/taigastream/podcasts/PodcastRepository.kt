//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.podcasts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private val Context.podcastDataStore by preferencesDataStore(name = "podcasts")

/**
 * The saved podcasts, their feeds, and where the listener is in each one.
 * Everything but the feeds is saved on the device.
 */
class PodcastRepository private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _podcasts = MutableStateFlow<List<Podcast>>(emptyList())
    val podcasts: StateFlow<List<Podcast>> = _podcasts.asStateFlow()

    private val _states = MutableStateFlow<Map<UUID, EpisodeState>>(emptyMap())
    val states: StateFlow<Map<UUID, EpisodeState>> = _states.asStateFlow()

    private val _feeds = MutableStateFlow<Map<UUID, PodcastFeed>>(emptyMap())
    val feeds: StateFlow<Map<UUID, PodcastFeed>> = _feeds.asStateFlow()

    private val _loading = MutableStateFlow<Set<UUID>>(emptySet())
    val loading: StateFlow<Set<UUID>> = _loading.asStateFlow()

    private val _failed = MutableStateFlow<Set<UUID>>(emptySet())
    val failed: StateFlow<Set<UUID>> = _failed.asStateFlow()

    /** Positions by "podcastId|episodeId", newest kept, capped. */
    private val positions = mutableMapOf<String, Pair<Double, Long>>()

    init {
        // Small and needed before anything is shown or played.
        val prefs = runBlocking { appContext.podcastDataStore.data.first() }
        _podcasts.value = prefs[PODCASTS_KEY]?.let { parsePodcasts(it) }.orEmpty()
        _states.value = prefs[STATES_KEY]?.let { parseStates(it) }.orEmpty()
        prefs[POSITIONS_KEY]?.let { parsePositions(it) }?.let { positions.putAll(it) }
    }

    // MARK: Podcasts

    fun podcast(at: Int): Podcast = _podcasts.value.getOrNull(at) ?: Podcast.EMPTY

    fun podcast(id: UUID): Podcast? = _podcasts.value.firstOrNull { it.id == id }

    fun savePodcast(podcast: Podcast, index: Int) {
        val current = _podcasts.value.toMutableList()
        if (index !in 0..current.size) return
        var saved: Podcast? = null
        when {
            podcast.feedUrl.isEmpty() -> {
                if (index == current.size) return
                forget(current.removeAt(index).id)
            }
            index == current.size -> {
                saved = podcast.copy(id = UUID.randomUUID())
                current += saved
            }
            else -> {
                val previous = current[index]
                saved = podcast.copy(id = previous.id)
                current[index] = saved
                if (previous.feedUrl != saved.feedUrl) forget(previous.id)
            }
        }
        _podcasts.value = current
        persistPodcasts()
        saved?.let { scope.launch { refreshFeed(it) } }
    }

    fun clearPodcast(index: Int) = savePodcast(Podcast.EMPTY, index)

    /** Saves a new order, for example after dragging a row. */
    fun reorder(ids: List<UUID>) {
        val byId = _podcasts.value.associateBy { it.id }
        val reordered = ids.mapNotNull { byId[it] }
        if (reordered.size != _podcasts.value.size || reordered == _podcasts.value) return
        _podcasts.value = reordered
        persistPodcasts()
    }

    private fun forget(id: UUID) {
        _feeds.update { it - id }
        _failed.update { it - id }
        if (_states.value.containsKey(id)) {
            _states.update { it - id }
            persistStates()
        }
        val prefix = "$id|"
        if (positions.keys.removeAll { it.startsWith(prefix) }) persistPositions()
    }

    // MARK: Feeds

    suspend fun refreshFeed(podcast: Podcast): PodcastFeed? {
        if (podcast.feedUrl.isEmpty()) return null
        _loading.update { it + podcast.id }
        try {
            val feed = PodcastFeedParser.fetch(podcast.feedUrl)
            if (feed == null) {
                if (_feeds.value[podcast.id] == null) _failed.update { it + podcast.id }
                return _feeds.value[podcast.id]
            }
            // The podcast may have been removed or re-pointed while loading.
            val current = podcast(podcast.id)
            if (current == null || current.feedUrl != podcast.feedUrl) return null
            _failed.update { it - podcast.id }
            _feeds.update { it + (podcast.id to feed) }
            fillDetails(current, feed)
            return feed
        } finally {
            _loading.update { it - podcast.id }
        }
    }

    suspend fun refreshAllFeeds() = coroutineScope {
        _podcasts.value.map { async { refreshFeed(it) } }.awaitAll()
        Unit
    }

    private fun fillDetails(podcast: Podcast, feed: PodcastFeed) {
        val index = _podcasts.value.indexOfFirst { it.id == podcast.id }
        if (index < 0) return
        val original = _podcasts.value[index]
        var updated = original
        if (updated.name.isBlank() && feed.title.isNotEmpty()) updated = updated.copy(name = feed.title)
        if (updated.artworkUrl.isEmpty() && feed.artworkUrl.isNotEmpty()) {
            updated = updated.copy(artworkUrl = feed.artworkUrl)
        }
        if (updated == original) return
        _podcasts.value = _podcasts.value.toMutableList().also { it[index] = updated }
        persistPodcasts()
    }

    // MARK: Episodes

    /**
     * The episode the play button starts: the one the listener is partway
     * through or picked by hand, otherwise the newest episode.
     */
    fun currentEpisode(podcast: Podcast): PodcastEpisode? {
        val state = _states.value[podcast.id]
        if (state != null && (state.isPinned || state.isInProgress)) return state.episode
        return _feeds.value[podcast.id]?.episodes?.firstOrNull() ?: state?.episode
    }

    /** Like [currentEpisode], but loads the feed first when nothing is known yet. */
    suspend fun resolveEpisode(podcast: Podcast): PodcastEpisode? {
        val state = _states.value[podcast.id]
        if (state != null && (state.isPinned || state.isInProgress)) return state.episode
        if (_feeds.value[podcast.id] == null) refreshFeed(podcast)
        return currentEpisode(podcast)
    }

    fun selectEpisode(episode: PodcastEpisode, podcast: Podcast) {
        _states.update {
            it + (podcast.id to EpisodeState(
                episode = episode,
                position = savedPosition(podcast.id, episode),
                isPinned = true,
                updatedAt = System.currentTimeMillis(),
            ))
        }
        persistStates()
    }

    fun savedPosition(podcastId: UUID, episode: PodcastEpisode): Double {
        val state = _states.value[podcastId]
        if (state != null && state.episode.id == episode.id) return state.position
        return positions[positionKey(podcastId, episode.id)]?.first ?: 0.0
    }

    fun recordProgress(
        podcastId: UUID,
        episode: PodcastEpisode,
        position: Double,
        duration: Double?,
        persist: Boolean,
    ) {
        if (podcast(podcastId) == null) return
        val stored = if (duration != null && duration.isFinite() && duration > 0) {
            episode.copy(duration = duration)
        } else {
            episode
        }
        val pinned = _states.value[podcastId]?.let { it.isPinned && it.episode.id == episode.id } ?: false
        val now = System.currentTimeMillis()
        _states.update { it + (podcastId to EpisodeState(stored, position, pinned, now)) }
        positions[positionKey(podcastId, episode.id)] = position to now
        if (persist) {
            persistStates()
            persistPositions()
        }
    }

    fun markFinished(podcastId: UUID, episode: PodcastEpisode) {
        if (_states.value[podcastId]?.episode?.id == episode.id) _states.update { it - podcastId }
        positions.remove(positionKey(podcastId, episode.id))
        persistStates()
        persistPositions()
    }

    fun progress(podcastId: UUID, episode: PodcastEpisode): Float? {
        val position = savedPosition(podcastId, episode)
        val duration = episode.duration ?: return null
        if (position <= 0 || duration <= 0) return null
        return (position / duration).coerceIn(0.0, 1.0).toFloat()
    }

    private fun positionKey(podcastId: UUID, episodeId: String) = "$podcastId|$episodeId"

    // MARK: Persistence

    private fun persistPodcasts() {
        val json = JSONArray().apply { _podcasts.value.forEach { put(it.toJson()) } }.toString()
        scope.launch { appContext.podcastDataStore.edit { it[PODCASTS_KEY] = json } }
    }

    private fun persistStates() {
        val json = JSONObject().apply {
            _states.value.forEach { (id, state) -> put(id.toString(), state.toJson()) }
        }.toString()
        scope.launch { appContext.podcastDataStore.edit { it[STATES_KEY] = json } }
    }

    private fun persistPositions() {
        if (positions.size > MAX_STORED_POSITIONS) {
            val keep = positions.entries.sortedByDescending { it.value.second }
                .take(MAX_STORED_POSITIONS).map { it.key }.toSet()
            positions.keys.retainAll(keep)
        }
        val json = JSONObject().apply {
            positions.forEach { (key, value) ->
                put(key, JSONObject().put("position", value.first).put("updatedAt", value.second))
            }
        }.toString()
        scope.launch { appContext.podcastDataStore.edit { it[POSITIONS_KEY] = json } }
    }

    private fun parsePodcasts(json: String): List<Podcast> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(Podcast::fromJson) }
    }.getOrDefault(emptyList())

    private fun parseStates(json: String): Map<UUID, EpisodeState> = runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().mapNotNull { key ->
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return@mapNotNull null
            val state = obj.optJSONObject(key)?.let(EpisodeState::fromJson) ?: return@mapNotNull null
            id to state
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun parsePositions(json: String): Map<String, Pair<Double, Long>> = runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().mapNotNull { key ->
            val value = obj.optJSONObject(key) ?: return@mapNotNull null
            key to (value.optDouble("position", 0.0) to value.optLong("updatedAt"))
        }.toMap()
    }.getOrDefault(emptyMap())

    companion object {
        @Volatile private var instance: PodcastRepository? = null

        fun get(context: Context): PodcastRepository =
            instance ?: synchronized(this) {
                instance ?: PodcastRepository(context.applicationContext).also { instance = it }
            }

        private const val MAX_STORED_POSITIONS = 300
        private val PODCASTS_KEY = stringPreferencesKey("podcasts")
        private val STATES_KEY = stringPreferencesKey("states")
        private val POSITIONS_KEY = stringPreferencesKey("positions")
    }
}
