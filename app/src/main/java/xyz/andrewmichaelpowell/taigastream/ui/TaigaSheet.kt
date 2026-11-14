//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.andrewmichaelpowell.taigastream.playback.NowPlaying

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaigaSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val showsArtwork = nowPlaying.isPlaying || nowPlaying.isPodcastActive
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            NowPlayingBackground(
                artwork = if (showsArtwork) nowPlaying.artwork else null,
                modifier = Modifier.matchParentSize(),
            )
            Column(modifier = Modifier.fillMaxWidth().windowInsetsPadding(BottomSheetDefaults.windowInsets)) {
                BottomSheetDefaults.DragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                content()
            }
        }
    }
}
