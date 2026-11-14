//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.playback

import android.graphics.Bitmap
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastEpisode

data class NowPlayingState(
    val isPlaying: Boolean = false,
    val currentStream: Int = 0,
    val artist: String = "",
    val title: String = "",
    val artwork: Bitmap? = null,
    val podcastId: UUID? = null,
    val episode: PodcastEpisode? = null,
    val position: Double = 0.0,
    val duration: Double? = null,
) {
    val isPodcastActive: Boolean get() = podcastId != null
}

object NowPlaying {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    fun update(transform: (NowPlayingState) -> NowPlayingState) {
        _state.update(transform)
    }
}
