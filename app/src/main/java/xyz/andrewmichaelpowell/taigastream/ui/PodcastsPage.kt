//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.coroutines.delay
import sh.calvin.reorderable.rememberReorderableLazyListState
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.playback.NowPlaying
import xyz.andrewmichaelpowell.taigastream.playback.PlaybackService
import xyz.andrewmichaelpowell.taigastream.podcasts.Podcast
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastFormat
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastRepository
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors
import kotlin.time.Duration.Companion.milliseconds

private sealed interface PodcastSheet {
    data object None : PodcastSheet
    data class Options(val index: Int) : PodcastSheet
    data class Search(val index: Int) : PodcastSheet
    data class ManualEntry(val index: Int, val name: String, val url: String) : PodcastSheet
    data class Episodes(val podcastId: UUID) : PodcastSheet
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun PodcastsPage(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val library = remember { PodcastRepository.get(context) }
    val podcasts by library.podcasts.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<PodcastSheet>(PodcastSheet.None) }

    var rows by remember { mutableStateOf(podcasts) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(podcasts) {
        if (!reorderState.isAnyItemDragging) rows = podcasts
    }
    LaunchedEffect(Unit) { library.refreshAllFeeds() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(rows, key = { it.id }) { podcast ->
            val index = rows.indexOf(podcast)
            ReorderableRow(
                state = reorderState,
                key = podcast.id,
                onDragStopped = { library.reorder(rows.map { it.id }) },
            ) {
                FaviconImage(podcast.artworkUrl, isConfigured = true, fallbackIcon = Icons.Filled.Mic)
                PodcastRow(
                    podcast = podcast,
                    library = library,
                    modifier = Modifier.weight(1f),
                    onClick = { sheet = PodcastSheet.Options(index) },
                )
                CircleButton(
                    onClick = { sheet = PodcastSheet.Episodes(podcast.id) },
                    modifier = Modifier.semantics { contentDescription = context.getString(R.string.episodes) },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = LocalTaigaStreamColors.current.label,
                    )
                }
                PodcastPlayButton(podcast = podcast, number = index + 1)
            }
        }
        item(key = "add") {
            AddRow(
                label = stringResource(R.string.add_podcast),
                onClick = { sheet = PodcastSheet.Options(podcasts.size) },
            )
        }
    }

    when (val current = sheet) {
        is PodcastSheet.Options -> {
            val podcast = library.podcast(current.index)
            PodcastOptionsSheet(
                index = current.index,
                hasPodcast = podcast.feedUrl.isNotEmpty(),
                onDismiss = { sheet = PodcastSheet.None },
                onSearch = { sheet = PodcastSheet.Search(current.index) },
                onEnterUrl = { sheet = PodcastSheet.ManualEntry(current.index, podcast.name, podcast.feedUrl) },
                onClear = {
                    library.clearPodcast(current.index)
                    sheet = PodcastSheet.None
                },
            )
        }
        is PodcastSheet.Search -> PodcastSearchSheet(
            onDismiss = { sheet = PodcastSheet.None },
            onSelect = { result ->
                library.savePodcast(
                    Podcast(feedUrl = result.feedUrl, name = result.name, artworkUrl = result.artworkUrl),
                    current.index,
                )
                sheet = PodcastSheet.None
            },
        )
        is PodcastSheet.ManualEntry -> ManualUrlSheet(
            initialName = current.name,
            initialUrl = current.url,
            nameIcon = Icons.Filled.Mic,
            onDismiss = { sheet = PodcastSheet.None },
            onSave = { name, url ->
                val existing = library.podcast(current.index)
                library.savePodcast(
                    Podcast(
                        feedUrl = url,
                        name = name,
                        artworkUrl = if (existing.feedUrl == url) existing.artworkUrl else "",
                    ),
                    current.index,
                )
                sheet = PodcastSheet.None
            },
        )
        is PodcastSheet.Episodes -> EpisodePickerSheet(
            podcastId = current.podcastId,
            onDismiss = { sheet = PodcastSheet.None },
        )
        PodcastSheet.None -> {}
    }
}

@Composable
private fun PodcastRow(
    podcast: Podcast,
    library: PodcastRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalTaigaStreamColors.current
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val loading by library.loading.collectAsStateWithLifecycle()
    val failed by library.failed.collectAsStateWithLifecycle()
    library.feeds.collectAsStateWithLifecycle().value
    library.states.collectAsStateWithLifecycle().value

    val episode = library.currentEpisode(podcast)
    val subtitle = if (episode == null) {
        when (podcast.id) {
            in loading -> stringResource(R.string.loading_episodes)
            in failed -> stringResource(R.string.couldnt_load_episodes)
            else -> null
        }
    } else {
        val isActive = nowPlaying.podcastId == podcast.id && nowPlaying.episode?.id == episode.id
        val position = if (isActive) nowPlaying.position else library.savedPosition(podcast.id, episode)
        val duration = if (isActive) nowPlaying.duration ?: episode.duration else episode.duration
        val detail = PodcastFormat.remaining(context, position, duration) ?: PodcastFormat.date(episode.publishedAt)
        listOfNotNull(episode.title.ifEmpty { null }, detail).joinToString(" · ")
    }

    Surface(onClick = onClick, shape = RowShape, color = frostedColor(), modifier = modifier.heightIn(min = 56.dp)) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    podcast.name.ifBlank { podcast.feedUrl },
                    color = colors.label,
                    maxLines = 1,
                    overflow = if (podcast.name.isBlank()) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tertiaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PodcastPlayButton(podcast: Podcast, number: Int) {
    val context = LocalContext.current
    val colors = LocalTaigaStreamColors.current
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val isPlaying = nowPlaying.isPlaying && nowPlaying.podcastId == podcast.id
    var isStarting by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) { if (isPlaying) isStarting = false }
    LaunchedEffect(isStarting) {
        if (isStarting) {
            delay(20_000.milliseconds)
            isStarting = false
        }
    }
    val label = if (isPlaying) {
        stringResource(R.string.pause)
    } else {
        stringResource(R.string.play_podcast, number)
    }
    CircleButton(
        onClick = {
            if (!isStarting) {
                isStarting = !isPlaying
                PlaybackService.startPlayPodcast(context, number - 1)
            }
        },
        isOn = isPlaying,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        when {
            isPlaying -> Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White)
            isStarting -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else -> Text("$number", color = colors.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
