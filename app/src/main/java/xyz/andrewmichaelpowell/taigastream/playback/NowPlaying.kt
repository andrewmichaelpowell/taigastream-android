//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.playback

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process now-playing state, observed by the Compose UI and the home-screen widget. On iOS the
 * widget runs in a separate extension process, so playback state has to round-trip through an App
 * Group `UserDefaults` (Taiga Stream Widget/WidgetView.swift:1958-1960). Android widgets run in
 * the same process as the app, so a plain in-memory `StateFlow` is the direct equivalent without
 * needing that indirection.
 */
data class NowPlayingState(
    val isPlaying: Boolean = false,
    /** 1-based slot number, matching `StreamInfo.currentStream`; 0 means nothing selected. */
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
