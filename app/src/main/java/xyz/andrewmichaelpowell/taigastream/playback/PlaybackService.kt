//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.scale
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xyz.andrewmichaelpowell.taigastream.MainActivity
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.StationRepository
import xyz.andrewmichaelpowell.taigastream.metadata.ArtworkFetcher
import xyz.andrewmichaelpowell.taigastream.metadata.FaviconArtwork
import xyz.andrewmichaelpowell.taigastream.metadata.IcyMetadataParser
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProviders
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.NetworkClient
import xyz.andrewmichaelpowell.taigastream.metadata.providers.IcecastProvider
import xyz.andrewmichaelpowell.taigastream.podcasts.EpisodeState
import xyz.andrewmichaelpowell.taigastream.podcasts.Podcast
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastEpisode
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastFormat
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastRepository
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: StationRepository
    private lateinit var podcasts: PodcastRepository
    private val serviceScope = CoroutineScope(SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentStreamUrl: okhttp3.HttpUrl? = null
    private var activeProvider: MetadataProvider? = null
    private var pollJob: Job? = null
    private var icyEnabled = false
    private var apiMetadataActive = false
    private var lastResultKey = ""

    private var hasRealArtwork = false
    private var artworkRequestId = 0
    private var fallbackRequestId = 0
    private val faviconArtworkCache = mutableMapOf<String, Bitmap>()
    private val browseArtworkCache = ConcurrentHashMap<String, ByteArray>()

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                player.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository.get(this)
        podcasts = PodcastRepository.get(this)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TaigaStream/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-Metadata" to "1"))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setSeekBackIncrementMs(SKIP_BACK_MS)
            .setSeekForwardIncrementMs(SKIP_FORWARD_MS)
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            addListener(playerListener)
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaLibrarySession.Builder(this, player, sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()
        addSession(mediaSession)

        serviceScope.launch {
            repository.stations
                .map { stations -> stations.map { Triple(it.url, it.name, it.faviconUrl) } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    mainHandler.post {
                        syncCurrentStream()
                        mediaSession.notifyChildrenChanged(RADIO_ID, configuredSlots().size, null)
                    }
                }
        }
        serviceScope.launch {
            podcasts.podcasts
                .map { list -> list.map { Triple(it.id, it.name, it.artworkUrl) } }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    mainHandler.post {
                        syncCurrentPodcast()
                        mediaSession.notifyChildrenChanged(PODCASTS_ID, podcasts.podcasts.value.size, null)
                    }
                }
        }

        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SLOT -> {
                val index = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
                if (index >= 0) playSlot(index)
            }
            ACTION_PLAY_PODCAST -> {
                val index = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
                if (index >= 0) playPodcastSlot(index)
            }
            ACTION_PLAY_EPISODE -> {
                val podcastId = intent.getStringExtra(EXTRA_PODCAST_ID)
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val episodeId = intent.getStringExtra(EXTRA_EPISODE_ID)
                if (podcastId != null && episodeId != null) playEpisode(podcastId, episodeId)
            }
            ACTION_STOP -> player.pause()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        saveProgress()
        mainHandler.removeCallbacks(progressTick)
        stopMetadataPolling()
        serviceScope.cancel()
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        removeSession(mediaSession)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    // Re-points the current stream number at the playing station after the
    // station list changes, or stops playback if that station was removed.
    private fun syncCurrentStream() {
        val playingUrl = currentStreamUrl ?: return
        val currentStream = NowPlaying.state.value.currentStream
        if (currentStream == 0) return
        val index = repository.stations.value.indexOfFirst { it.url.toHttpUrlOrNull() == playingUrl }
        if (index < 0) {
            if (player.isPlaying) player.pause()
            return
        }
        if (currentStream == index + 1) return
        NowPlaying.update { it.copy(currentStream = index + 1) }
        player.currentMediaItem?.let { item ->
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                item.buildUpon().setMediaId(slotMediaId(index)).build(),
            )
        }
    }

    private fun fallbackTitle(slot: Int): String {
        val name = repository.stations.value.getOrNull(slot - 1)?.name?.trim().orEmpty()
        return name.ifEmpty { getString(R.string.stream_title, slot) }
    }

    private fun playSlot(index: Int) {
        val current = NowPlaying.state.value
        if (!current.isPodcastActive && player.isPlaying && current.currentStream == index + 1) {
            player.pause()
            return
        }

        val mediaItem = prepareSlot(index) ?: return
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    private fun prepareSlot(index: Int): MediaItem? {
        val station = repository.stations.value.getOrNull(index) ?: return null
        if (station.url.isEmpty()) return null

        deactivatePodcast()
        stopMetadataPolling()
        currentStreamUrl = station.url.toHttpUrlOrNull()
        hasRealArtwork = false
        artworkRequestId++
        fallbackRequestId++

        NowPlaying.update {
            NowPlayingState(isPlaying = false, currentStream = index + 1, artist = "", title = "", artwork = null)
        }
        setFallbackArtwork()

        val initialMetadata = MediaMetadata.Builder()
            .setTitle(fallbackTitle(index + 1))
            .setArtist(getString(R.string.app_name))
        NowPlaying.state.value.artwork?.let {
            initialMetadata.setArtworkData(bitmapToBytes(it), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(slotMediaId(index))
            .setUri(station.url)
            .setMediaMetadata(initialMetadata.build())
            .build()
    }

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(playerCommands(NowPlaying.state.value.isPodcastActive))
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val items: List<MediaItem> = when {
                    parentId == ROOT_ID -> listOf(
                        folderItem(RADIO_ID, getString(R.string.radio), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS),
                        folderItem(PODCASTS_ID, getString(R.string.podcasts), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
                    )
                    parentId == RADIO_ID -> configuredSlots().map { index ->
                        async { browseItem(index) }
                    }.awaitAll()
                    parentId == PODCASTS_ID -> podcasts.podcasts.value.map { podcast ->
                        async { browsePodcastItem(podcast) }
                    }.awaitAll()
                    parentId.startsWith(PODCAST_ID_PREFIX) -> {
                        val podcast = podcastFromMediaId(parentId)
                        if (podcast == null) {
                            future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                            return@launch
                        }
                        browseEpisodeItems(podcast)
                    }
                    else -> {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                        return@launch
                    }
                }
                val from = (page * pageSize).coerceIn(0, items.size)
                val to = (from + pageSize).coerceIn(from, items.size)
                future.set(LibraryResult.ofItemList(items.subList(from, to), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val index = slotIndex(mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch { future.set(LibraryResult.ofItem(browseItem(index), null)) }
            return future
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val selected = mediaItems.getOrNull(startIndex.coerceAtLeast(0))
            if (selected?.mediaId?.startsWith(EPISODE_ID_PREFIX) == true) {
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                mainHandler.post {
                    val prepared = episodeFromMediaId(selected.mediaId)?.let { (podcast, episode) ->
                        preparePodcastItem(podcast, episode)
                    }
                    if (prepared == null) {
                        future.setException(IllegalArgumentException("Unknown episode"))
                    } else {
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(listOf(prepared.first), 0, prepared.second),
                        )
                    }
                }
                return future
            }
            val index = slotIndex(selected?.mediaId)
            val item = index?.let { prepareSlot(it) }
                ?: return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET),
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                slotIndex(item.mediaId)?.let { prepareSlot(it) } ?: item
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }
    }

    private fun configuredSlots(): List<Int> =
        repository.stations.value.indices.filter { repository.stations.value[it].url.isNotEmpty() }

    private suspend fun browseItem(index: Int): MediaItem {
        val station = repository.stations.value[index]
        val metadata = MediaMetadata.Builder()
            .setTitle(fallbackTitle(index + 1))
            .setSubtitle(getString(R.string.stream_title, index + 1))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
        browseArtwork(station.faviconUrl.trim())?.let {
            metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId(slotMediaId(index))
            .setMediaMetadata(metadata.build())
            .build()
    }

    private suspend fun browseArtwork(faviconUrl: String): ByteArray? {
        val key = faviconUrl.ifEmpty { APP_ICON_KEY }
        browseArtworkCache[key]?.let { return it }
        val source = if (faviconUrl.toHttpUrlOrNull() == null) {
            appIconBitmap()
        } else {
            withTimeoutOrNull(BROWSE_ARTWORK_TIMEOUT_MS.milliseconds) { fetchFavicon(faviconUrl) }
                ?.let { FaviconArtwork.render(it) }
                ?: return null
        } ?: return null
        val bytes = bitmapToBytes(source.scale(BROWSE_ARTWORK_SIDE, BROWSE_ARTWORK_SIDE))
        browseArtworkCache[key] = bytes
        return bytes
    }

    private suspend fun fetchFavicon(url: String): Bitmap? = suspendCancellableCoroutine { continuation ->
        NetworkClient.fetchBytes(
            NetworkClient.get(url),
            onFailure = { if (continuation.isActive) continuation.resume(null) },
        ) { bytes ->
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            if (continuation.isActive) continuation.resume(bitmap)
        }
    }

    private fun slotMediaId(index: Int) = "$SLOT_ID_PREFIX${index + 1}"

    private fun slotIndex(mediaId: String?): Int? =
        mediaId?.removePrefix(SLOT_ID_PREFIX)
            ?.takeIf { mediaId.startsWith(SLOT_ID_PREFIX) }
            ?.toIntOrNull()
            ?.minus(1)
            ?.takeIf { it in repository.stations.value.indices && repository.stations.value[it].url.isNotEmpty() }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            NowPlaying.update { it.copy(isPlaying = isPlaying) }
            if (NowPlaying.state.value.isPodcastActive) {
                readPosition()
                if (!isPlaying) saveProgress()
                return
            }
            if (isPlaying) {
                currentStreamUrl?.let { startMetadataPolling(it) }
            } else {
                stopMetadataPolling()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && NowPlaying.state.value.isPodcastActive) {
                episodeDidFinish()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            NowPlaying.update { it.copy(isPlaying = false) }
            stopMetadataPolling()
        }

        override fun onMetadata(metadata: Metadata) {
            if (!icyEnabled || apiMetadataActive) return
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val (artist, title) = IcyMetadataParser.parse(entry.title)
                    handleMetadataResult(MetadataResult(artist, title))
                }
            }
        }
    }

    private fun startMetadataPolling(streamUrl: okhttp3.HttpUrl) {
        stopMetadataPolling()
        val provider = MetadataProviders.find(streamUrl) ?: return
        activeProvider = provider
        icyEnabled = provider is IcecastProvider

        val onResult: (MetadataResult) -> Unit = { result -> handleMetadataResult(result) }
        provider.poll(streamUrl, onResult)

        val interval = provider.pollInterval ?: return
        pollJob = serviceScope.launch {
            while (isActive) {
                delay((interval * 1000).milliseconds)
                provider.poll(streamUrl, onResult)
            }
        }
    }

    private fun stopMetadataPolling() {
        pollJob?.cancel()
        pollJob = null
        activeProvider?.cancel()
        activeProvider = null
        icyEnabled = false
        apiMetadataActive = false
        lastResultKey = ""
    }

    private fun handleMetadataResult(result: MetadataResult) {
        mainHandler.post {
            val slot = NowPlaying.state.value.currentStream
            val combinedKey = "${result.artist}|${result.title}"
            if (result.title.isEmpty() || combinedKey == lastResultKey) return@post
            lastResultKey = combinedKey
            apiMetadataActive = true

            val resolvedArtist = result.artist.ifEmpty { getString(R.string.app_name) }
            val resolvedTitle = result.title.ifEmpty { fallbackTitle(slot) }
            val requestId = ++artworkRequestId

            NowPlaying.update { it.copy(artist = resolvedArtist, title = resolvedTitle) }
            updateSessionMetadata(resolvedArtist, resolvedTitle, NowPlaying.state.value.artwork)

            ArtworkFetcher.resolve(
                artist = resolvedArtist,
                title = resolvedTitle,
                artworkUrl = result.artworkUrl,
                onFailure = {
                    mainHandler.post {
                        if (artworkRequestId != requestId) return@post
                        hasRealArtwork = false
                        setFallbackArtwork()
                    }
                },
                onBitmap = { bitmap ->
                    mainHandler.post {
                        if (artworkRequestId == requestId) applyArtworkBitmap(bitmap, isReal = true)
                    }
                },
            )
        }
    }

    private fun setFallbackArtwork() {
        if (hasRealArtwork || NowPlaying.state.value.isPodcastActive) return
        val requestId = ++fallbackRequestId
        val slot = NowPlaying.state.value.currentStream
        val faviconUrl = repository.stations.value.getOrNull(slot - 1)?.faviconUrl?.trim().orEmpty()
        if (faviconUrl.isEmpty() || faviconUrl.toHttpUrlOrNull() == null) {
            applyArtworkBitmap(appIconBitmap(), isReal = false)
            return
        }

        faviconArtworkCache[faviconUrl]?.let {
            applyArtworkBitmap(it, isReal = false)
            return
        }

        applyArtworkBitmap(appIconBitmap(), isReal = false)

        loadFaviconArtwork(faviconUrl) { composed ->
            if (fallbackRequestId != requestId || hasRealArtwork) return@loadFaviconArtwork
            applyArtworkBitmap(composed ?: appIconBitmap(), isReal = false)
        }
    }

    private fun loadFaviconArtwork(url: String, onResult: (Bitmap?) -> Unit) {
        NetworkClient.fetchBytes(
            NetworkClient.get(url),
            onFailure = { mainHandler.post { onResult(null) } },
        ) { bytes ->
            val composed = runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { FaviconArtwork.render(it) }
            }.getOrNull()
            mainHandler.post {
                if (composed != null) faviconArtworkCache[url] = composed
                onResult(composed)
            }
        }
    }

    private fun applyArtworkBitmap(bitmap: Bitmap?, isReal: Boolean) {
        if (isReal) {
            hasRealArtwork = true
        } else if (hasRealArtwork) {
            return
        }
        NowPlaying.update { it.copy(artwork = bitmap) }
        val state = NowPlaying.state.value
        updateSessionMetadata(
            state.artist.ifEmpty { getString(R.string.app_name) },
            state.title.ifEmpty { fallbackTitle(state.currentStream) },
            bitmap,
        )
    }

    private fun updateSessionMetadata(artist: String, title: String, artwork: Bitmap?) {
        val currentItem = player.currentMediaItem ?: return
        if (currentItem.mediaId != slotMediaId(NowPlaying.state.value.currentStream - 1)) return
        val metadataBuilder = currentItem.mediaMetadata.buildUpon()
            .setTitle(title)
            .setArtist(artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        if (artwork != null) {
            metadataBuilder.setArtworkData(bitmapToBytes(artwork), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val updatedItem = currentItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()
        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }

    private fun appIconBitmap(): Bitmap? = FaviconArtwork.appIcon(this)

    // MARK: Podcasts

    private val podcastArtworkCache = mutableMapOf<String, Bitmap>()
    private var ticksSincePersist = 0

    private fun playerCommands(isPodcast: Boolean): Player.Commands {
        val builder = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_GET_TIMELINE)
        if (isPodcast) {
            builder.add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        }
        return builder.build()
    }

    private fun updateAvailableCommands() {
        val commands = playerCommands(NowPlaying.state.value.isPodcastActive)
        mediaSession.connectedControllers.forEach { controller ->
            mediaSession.setAvailableCommands(
                controller,
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                commands,
            )
        }
    }

    private fun playPodcastSlot(index: Int) {
        val podcast = podcasts.podcast(index)
        if (podcast.feedUrl.isEmpty()) return
        val state = NowPlaying.state.value
        if (state.podcastId == podcast.id && isEpisodeLoaded()) {
            if (player.isPlaying) {
                player.pause()
                return
            }
            if (state.episode?.id == podcasts.currentEpisode(podcast)?.id) {
                player.play()
                return
            }
        }
        serviceScope.launch {
            val episode = podcasts.resolveEpisode(podcast) ?: return@launch
            mainHandler.post { startEpisode(podcast, episode) }
        }
    }

    private fun playEpisode(podcastId: UUID, episodeId: String) {
        val podcast = podcasts.podcast(podcastId) ?: return
        val episode = findEpisode(podcastId, episodeId) ?: return
        podcasts.selectEpisode(episode, podcast)
        val state = NowPlaying.state.value
        if (state.podcastId == podcastId && state.episode?.id == episodeId && isEpisodeLoaded()) {
            if (!player.isPlaying) player.play()
            return
        }
        startEpisode(podcast, episode)
    }

    private fun startEpisode(podcast: Podcast, episode: PodcastEpisode) {
        val (item, startMs) = preparePodcastItem(podcast, episode) ?: return
        player.setMediaItem(item, startMs)
        player.prepare()
        player.play()
    }

    private fun preparePodcastItem(podcast: Podcast, episode: PodcastEpisode): Pair<MediaItem, Long>? {
        if (episode.audioUrl.isEmpty()) return null
        readPosition()
        saveProgress()
        stopMetadataPolling()
        currentStreamUrl = null
        hasRealArtwork = false
        artworkRequestId++
        fallbackRequestId++

        var resumeAt = podcasts.savedPosition(podcast.id, episode)
        val known = episode.duration
        if (known != null && resumeAt >= known - EpisodeState.FINISHED_THRESHOLD) resumeAt = 0.0
        podcasts.recordProgress(podcast.id, episode, resumeAt, known, persist = true)

        val artworkUrl = listOfNotNull(episode.artworkUrl, podcast.artworkUrl)
            .map { it.trim() }
            .firstOrNull { it.toHttpUrlOrNull() != null }
        val cachedArtwork = artworkUrl?.let { podcastArtworkCache[it] }
        NowPlaying.update {
            NowPlayingState(
                isPlaying = false,
                currentStream = 0,
                artist = podcast.name,
                title = episode.title,
                artwork = cachedArtwork ?: appIconBitmap(),
                podcastId = podcast.id,
                episode = episode,
                position = resumeAt,
                duration = known,
            )
        }
        updateAvailableCommands()
        startProgressTicks()

        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title.ifEmpty { podcast.name })
            .setArtist(podcast.name.ifEmpty { getString(R.string.app_name) })
            .setAlbumTitle(podcast.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        (cachedArtwork ?: appIconBitmap())?.let {
            metadata.setArtworkData(bitmapToBytes(it), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val item = MediaItem.Builder()
            .setMediaId(episodeMediaId(podcast.id, episode.id))
            .setUri(episode.audioUrl)
            .setMediaMetadata(metadata.build())
            .build()
        if (artworkUrl != null && cachedArtwork == null) loadPodcastArtwork(artworkUrl)
        return item to (resumeAt * 1000).toLong()
    }

    private fun loadPodcastArtwork(url: String) {
        val requestId = artworkRequestId
        NetworkClient.fetchBytes(NetworkClient.get(url)) { bytes ->
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                ?.let { limitSize(it) }
            mainHandler.post {
                if (bitmap == null) return@post
                podcastArtworkCache[url] = bitmap
                if (artworkRequestId == requestId) applyPodcastArtwork(bitmap)
            }
        }
    }

    private fun limitSize(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= PODCAST_ARTWORK_SIDE) return bitmap
        val factor = PODCAST_ARTWORK_SIDE.toFloat() / longest
        return bitmap.scale((bitmap.width * factor).toInt(), (bitmap.height * factor).toInt())
    }

    private fun applyPodcastArtwork(bitmap: Bitmap) {
        val state = NowPlaying.state.value
        val podcastId = state.podcastId ?: return
        val episode = state.episode ?: return
        NowPlaying.update { it.copy(artwork = bitmap) }
        val item = player.currentMediaItem ?: return
        if (item.mediaId != episodeMediaId(podcastId, episode.id)) return
        val updated = item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setArtworkData(bitmapToBytes(bitmap), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build(),
            )
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, updated)
    }

    private val progressTick = object : Runnable {
        override fun run() {
            if (!NowPlaying.state.value.isPodcastActive) return
            if (player.isPlaying) {
                readPosition()
                ticksSincePersist++
                if (ticksSincePersist >= PERSIST_EVERY) {
                    ticksSincePersist = 0
                    saveProgress()
                }
            }
            mainHandler.postDelayed(this, 1000)
        }
    }

    private fun startProgressTicks() {
        mainHandler.removeCallbacks(progressTick)
        ticksSincePersist = 0
        mainHandler.postDelayed(progressTick, 1000)
    }

    private fun isEpisodeLoaded(): Boolean {
        val state = NowPlaying.state.value
        val podcastId = state.podcastId ?: return false
        val episode = state.episode ?: return false
        return player.currentMediaItem?.mediaId == episodeMediaId(podcastId, episode.id)
    }

    private fun readPosition() {
        if (!isEpisodeLoaded()) return
        val position = player.currentPosition.coerceAtLeast(0) / 1000.0
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }?.div(1000.0)
        NowPlaying.update { it.copy(position = position, duration = duration ?: it.duration) }
    }

    private fun saveProgress() {
        val state = NowPlaying.state.value
        val podcastId = state.podcastId ?: return
        val episode = state.episode ?: return
        val duration = state.duration
        if (duration != null && duration > 0 && state.position >= duration - EpisodeState.FINISHED_THRESHOLD) {
            podcasts.markFinished(podcastId, episode)
            return
        }
        podcasts.recordProgress(podcastId, episode, state.position, duration, persist = true)
    }

    private fun episodeDidFinish() {
        val state = NowPlaying.state.value
        val podcastId = state.podcastId ?: return
        val episode = state.episode ?: return
        podcasts.markFinished(podcastId, episode)
        NowPlaying.update { it.copy(position = 0.0) }
        player.pause()
        player.seekTo(0)
    }

    private fun deactivatePodcast() {
        if (!NowPlaying.state.value.isPodcastActive) return
        readPosition()
        saveProgress()
        mainHandler.removeCallbacks(progressTick)
        NowPlaying.update { it.copy(podcastId = null, episode = null, position = 0.0, duration = null) }
        updateAvailableCommands()
    }

    private fun syncCurrentPodcast() {
        val podcastId = NowPlaying.state.value.podcastId ?: return
        if (podcasts.podcast(podcastId) != null) return
        player.pause()
        deactivatePodcast()
        player.clearMediaItems()
    }

    private fun findEpisode(podcastId: UUID, episodeId: String): PodcastEpisode? =
        podcasts.feeds.value[podcastId]?.episodes?.firstOrNull { it.id == episodeId }
            ?: podcasts.states.value[podcastId]?.episode?.takeIf { it.id == episodeId }

    private fun episodeMediaId(podcastId: UUID, episodeId: String) = "$EPISODE_ID_PREFIX$podcastId|$episodeId"

    private fun episodeFromMediaId(mediaId: String): Pair<Podcast, PodcastEpisode>? {
        val rest = mediaId.removePrefix(EPISODE_ID_PREFIX)
        val podcastId = runCatching { UUID.fromString(rest.substringBefore('|')) }.getOrNull() ?: return null
        val podcast = podcasts.podcast(podcastId) ?: return null
        val episode = findEpisode(podcastId, rest.substringAfter('|')) ?: return null
        podcasts.selectEpisode(episode, podcast)
        return podcast to episode
    }

    private fun podcastFromMediaId(mediaId: String): Podcast? =
        runCatching { UUID.fromString(mediaId.removePrefix(PODCAST_ID_PREFIX)) }.getOrNull()
            ?.let { podcasts.podcast(it) }

    // MARK: Android Auto

    private fun folderItem(id: String, title: String, mediaType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build(),
            )
            .build()

    private suspend fun browsePodcastItem(podcast: Podcast): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(podcast.name.ifEmpty { podcast.feedUrl })
            .setSubtitle(podcasts.currentEpisode(podcast)?.title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
        browseArtwork(podcast.artworkUrl.trim())?.let {
            metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        return MediaItem.Builder()
            .setMediaId("$PODCAST_ID_PREFIX${podcast.id}")
            .setMediaMetadata(metadata.build())
            .build()
    }

    private suspend fun browseEpisodeItems(podcast: Podcast): List<MediaItem> {
        if (podcasts.feeds.value[podcast.id] == null) podcasts.refreshFeed(podcast)
        val episodes = podcasts.feeds.value[podcast.id]?.episodes.orEmpty().take(MAX_BROWSE_EPISODES)
        val artwork = browseArtwork(podcast.artworkUrl.trim())
        return episodes.map { episode ->
            val progress = podcasts.progress(podcast.id, episode)
            val extras = Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                    if (progress == null) {
                        MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                    } else {
                        MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    },
                )
                progress?.let { putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, it.toDouble()) }
            }
            val position = podcasts.savedPosition(podcast.id, episode)
            val subtitle = listOfNotNull(
                PodcastFormat.date(episode.publishedAt),
                PodcastFormat.remaining(this, position, episode.duration)
                    ?: episode.duration?.let { PodcastFormat.duration(it) },
            ).joinToString(" · ")
            val metadata = MediaMetadata.Builder()
                .setTitle(episode.title.ifEmpty { episode.audioUrl })
                .setSubtitle(subtitle)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setExtras(extras)
            artwork?.let { metadata.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            MediaItem.Builder()
                .setMediaId(episodeMediaId(podcast.id, episode.id))
                .setMediaMetadata(metadata.build())
                .build()
        }
    }

    companion object {
        const val ACTION_PLAY_SLOT = "xyz.andrewmichaelpowell.taigastream.action.PLAY_SLOT"
        const val ACTION_STOP = "xyz.andrewmichaelpowell.taigastream.action.STOP"
        const val ACTION_PLAY_PODCAST = "xyz.andrewmichaelpowell.taigastream.action.PLAY_PODCAST"
        const val ACTION_PLAY_EPISODE = "xyz.andrewmichaelpowell.taigastream.action.PLAY_EPISODE"
        const val EXTRA_SLOT_INDEX = "slot_index"
        const val EXTRA_PODCAST_ID = "podcast_id"
        const val EXTRA_EPISODE_ID = "episode_id"

        private const val ROOT_ID = "root"
        private const val RADIO_ID = "radio"
        private const val PODCASTS_ID = "podcasts"
        private const val PODCAST_ID_PREFIX = "podcast_"
        private const val EPISODE_ID_PREFIX = "episode_"
        private const val SKIP_BACK_MS = 15_000L
        private const val SKIP_FORWARD_MS = 30_000L
        private const val PERSIST_EVERY = 10
        private const val MAX_BROWSE_EPISODES = 50
        private const val PODCAST_ARTWORK_SIDE = 800
        private const val SLOT_ID_PREFIX = "slot_"
        private const val APP_ICON_KEY = "app_icon"
        private const val BROWSE_ARTWORK_SIDE = 128
        private const val BROWSE_ARTWORK_TIMEOUT_MS = 3000L

        fun playSlotIntent(context: Context, index: Int): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY_SLOT)
                .putExtra(EXTRA_SLOT_INDEX, index)

        fun startPlaySlot(context: Context, index: Int) {
            context.startService(playSlotIntent(context, index))
        }

        fun startPlayPodcast(context: Context, index: Int) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_PODCAST)
                    .putExtra(EXTRA_SLOT_INDEX, index),
            )
        }

        fun startPlayEpisode(context: Context, podcastId: UUID, episodeId: String) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_EPISODE)
                    .putExtra(EXTRA_PODCAST_ID, podcastId.toString())
                    .putExtra(EXTRA_EPISODE_ID, episodeId),
            )
        }
    }
}
