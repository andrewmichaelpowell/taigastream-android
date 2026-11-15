//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.cast.MediaRouteButton
import androidx.media3.cast.rememberMediaRouteButtonState
import androidx.media3.common.util.UnstableApi
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.StationRepository
import xyz.andrewmichaelpowell.taigastream.metadata.FaviconArtwork
import xyz.andrewmichaelpowell.taigastream.playback.NowPlaying
import xyz.andrewmichaelpowell.taigastream.playback.PlaybackService
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastRepository
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

@Composable
fun NowPlayingPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val stations by StationRepository.get(context).stations.collectAsStateWithLifecycle()
    val podcasts by PodcastRepository.get(context).podcasts.collectAsStateWithLifecycle()

    if (nowPlaying.isPodcastActive) {
        val podcastName = podcasts.firstOrNull { it.id == nowPlaying.podcastId }?.name.orEmpty()
        val episodeTitle = nowPlaying.episode?.title.orEmpty()
        PanelLayout(
            title = episodeTitle.ifEmpty { podcastName },
            subtitle = podcastName.ifEmpty { "Taiga Stream" },
            modifier = modifier,
        ) {
            PlayButton(
                if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
            ) { PlaybackService.togglePlayback(context) }
        }
    } else if (stations.getOrNull(nowPlaying.currentStream - 1) == null) {
        PanelLayout(
            title = "Taiga Stream",
            subtitle = "",
            modifier = modifier,
            showsAppIcon = true,
        ) {
            PlayButton(Icons.Filled.PlayArrow, stringResource(R.string.play), enabled = false) {}
        }
    } else {
        val station = stations[nowPlaying.currentStream - 1]
        val stationName = station.name.trim().ifEmpty {
            stringResource(R.string.stream_title, nowPlaying.currentStream)
        }
        PanelLayout(
            title = nowPlaying.title.ifEmpty { stationName },
            subtitle = nowPlaying.artist.ifEmpty { "Taiga Stream" },
            modifier = modifier,
        ) {
            PlayButton(
                if (nowPlaying.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                stringResource(if (nowPlaying.isPlaying) R.string.stop else R.string.play),
            ) { PlaybackService.togglePlayback(context) }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PanelLayout(
    title: String,
    subtitle: String,
    modifier: Modifier,
    showsAppIcon: Boolean = false,
    button: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalTaigaStreamColors.current
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val appIcon = remember { FaviconArtwork.appIcon(context) }
    val artwork = if (showsAppIcon) appIcon else nowPlaying.artwork ?: appIcon
    Surface(
        shape = RowShape,
        color = colors.secondarySystemBackground.copy(alpha = 0.9f),
        contentColor = colors.label,
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.label.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            button()
            MediaRouteButton(
                modifier = Modifier.size(48.dp),
                state = rememberMediaRouteButtonState(),
            )
        }
    }
}

@Composable
private fun PlayButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = colors.label.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(32.dp),
        )
    }
}
