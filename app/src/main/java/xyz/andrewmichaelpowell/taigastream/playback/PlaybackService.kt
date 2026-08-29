//  Taiga Stream
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
import androidx.core.graphics.drawable.toBitmap
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import xyz.andrewmichaelpowell.taigastream.MainActivity
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.StationRepository
import xyz.andrewmichaelpowell.taigastream.metadata.ArtworkFetcher
import xyz.andrewmichaelpowell.taigastream.metadata.IcyMetadataParser
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProvider
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataProviders
import xyz.andrewmichaelpowell.taigastream.metadata.MetadataResult
import xyz.andrewmichaelpowell.taigastream.metadata.providers.IcecastProvider
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var repository: StationRepository
    private val serviceScope = CoroutineScope(SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentStreamUrl: okhttp3.HttpUrl? = null
    private var activeProvider: MetadataProvider? = null
    private var pollJob: Job? = null
    private var icyEnabled = false
    private var apiMetadataActive = false
    private var lastResultKey = ""

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
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivity)
            .build()
        addSession(mediaSession)

        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

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

    private fun playSlot(index: Int) {
        // Checked before the station lookup below: if this slot is already playing, toggle it
        // off regardless of whether its preset was since cleared — otherwise clearing a station
        // mid-playback would leave it stuck playing with no way to stop it from this button.
        val current = NowPlaying.state.value
        if (player.isPlaying && current.currentStream == index + 1) {
            player.pause()
            return
        }

        val station = repository.stations.value.getOrNull(index) ?: return
        if (station.url.isEmpty()) return

        stopMetadataPolling()
        currentStreamUrl = station.url.toHttpUrlOrNull()

        val initialMetadata = MediaMetadata.Builder()
            .setTitle(getString(R.string.stream_title, index + 1))
            .setArtist(getString(R.string.app_name))
            .build()
        player.setMediaItem(MediaItem.Builder().setUri(station.url).setMediaMetadata(initialMetadata).build())
        player.prepare()

        NowPlaying.update {
            NowPlayingState(isPlaying = false, currentStream = index + 1, artist = "", title = "", artwork = null)
        }
        applyArtworkBitmap(appIconBitmap())

        player.play()
    }

    private val sessionCallback = object : MediaSession.Callback {
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
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }
    }

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
            val resolvedTitle = result.title.ifEmpty { getString(R.string.stream_title, slot) }

            NowPlaying.update { it.copy(artist = resolvedArtist, title = resolvedTitle) }
            updateSessionMetadata(resolvedArtist, resolvedTitle, NowPlaying.state.value.artwork)

            ArtworkFetcher.resolve(
                artist = resolvedArtist,
                title = resolvedTitle,
                artworkUrl = result.artworkUrl,
                onFailure = { mainHandler.post { applyArtworkBitmap(appIconBitmap()) } },
                onBitmap = { bitmap -> mainHandler.post { applyArtworkBitmap(bitmap) } },
            )
        }
    }

    private fun applyArtworkBitmap(bitmap: Bitmap?) {
        NowPlaying.update { it.copy(artwork = bitmap) }
        val state = NowPlaying.state.value
        updateSessionMetadata(
            state.artist.ifEmpty { getString(R.string.app_name) },
            state.title.ifEmpty { getString(R.string.stream_title, state.currentStream) },
            bitmap,
        )
    }

    private fun updateSessionMetadata(artist: String, title: String, artwork: Bitmap?) {
        val currentItem = player.currentMediaItem ?: return
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

    private fun appIconBitmap(): Bitmap? =
        runCatching {
            val drawable = packageManager.getApplicationIcon(packageName)
            drawable.toBitmap()
        }.getOrNull() ?: runCatching {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        }.getOrNull()

    companion object {
        const val ACTION_PLAY_SLOT = "xyz.andrewmichaelpowell.taigastream.action.PLAY_SLOT"
        const val ACTION_STOP = "xyz.andrewmichaelpowell.taigastream.action.STOP"
        const val EXTRA_SLOT_INDEX = "slot_index"

        fun playSlotIntent(context: Context, index: Int): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY_SLOT)
                .putExtra(EXTRA_SLOT_INDEX, index)

        fun startPlaySlot(context: Context, index: Int) {
            context.startService(playSlotIntent(context, index))
        }
    }
}
