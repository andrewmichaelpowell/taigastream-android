//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.playback

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NowPlayingState(
    val isPlaying: Boolean = false,
    val currentStream: Int = 0,
    val artist: String = "",
    val title: String = "",
    val artwork: Bitmap? = null,
)

object NowPlaying {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state.asStateFlow()

    fun update(transform: (NowPlayingState) -> NowPlayingState) {
        _state.update(transform)
    }
}
