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
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var repository: StationRepository
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

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("TaigaStream/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-Metadata" to "1"))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
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
                        mediaSession.notifyChildrenChanged(ROOT_ID, configuredSlots().size, null)
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
            ACTION_STOP -> player.pause()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stopMetadataPolling()
        serviceScope.cancel()
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        removeSession(mediaSession)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    private fun fallbackTitle(slot: Int): String {
        val name = repository.stations.value.getOrNull(slot - 1)?.name?.trim().orEmpty()
        return name.ifEmpty { getString(R.string.stream_title, slot) }
    }

    private fun playSlot(index: Int) {
        val current = NowPlaying.state.value
        if (player.isPlaying && current.currentStream == index + 1) {
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
            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SET_MEDIA_ITEM)
                .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_GET_TIMELINE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(playerCommands)
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
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val items = configuredSlots().map { index ->
                    async { browseItem(index) }
                }.awaitAll()
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
            if (isPlaying) {
                currentStreamUrl?.let { startMetadataPolling(it) }
            } else {
                stopMetadataPolling()
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
        if (hasRealArtwork) return
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

    companion object {
        const val ACTION_PLAY_SLOT = "xyz.andrewmichaelpowell.taigastream.action.PLAY_SLOT"
        const val ACTION_STOP = "xyz.andrewmichaelpowell.taigastream.action.STOP"
        const val EXTRA_SLOT_INDEX = "slot_index"

        private const val ROOT_ID = "root"
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
    }
}
