//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.coroutines.launch
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.playback.NowPlaying
import xyz.andrewmichaelpowell.taigastream.playback.PlaybackService
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastEpisode
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastFormat
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastRepository
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastSearchClient
import xyz.andrewmichaelpowell.taigastream.podcasts.PodcastSearchResult
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastOptionsSheet(
    index: Int,
    hasPodcast: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onEnterUrl: () -> Unit,
    onClear: () -> Unit,
) {
    TaigaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.podcast_title, index + 1),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            OptionButton(stringResource(R.string.search)) { onSearch() }
            OptionButton(stringResource(R.string.enter_url)) { onEnterUrl() }
            if (hasPodcast) OptionButton(stringResource(R.string.clear), destructive = true) { onClear() }
            OptionButton(stringResource(R.string.cancel)) { onDismiss() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastSearchSheet(onDismiss: () -> Unit, onSelect: (PodcastSearchResult) -> Unit) {
    val scope = rememberCoroutineScope()
    val colors = LocalTaigaStreamColors.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<PodcastSearchResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    val canSearch = query.isNotBlank() && !isLoading

    fun runSearch() {
        if (!canSearch) return
        hasSearched = true
        isLoading = true
        results = emptyList()
        scope.launch {
            results = PodcastSearchClient.search(query.trim())
            isLoading = false
        }
    }

    TaigaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaigaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                )
                OptionButton(enabled = canSearch, onClick = { runSearch() }) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(R.string.search),
                        fontWeight = FontWeight.Bold,
                        color = if (canSearch) colors.label else colors.tertiaryLabel,
                    )
                }
                OptionButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            }
            if (hasSearched && results.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), color = colors.tertiaryLabel)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { it.feedUrl }) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(result) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FaviconImage(result.artworkUrl, isConfigured = true, fallbackIcon = Icons.Filled.Mic)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                listOf(result.author, result.genre).filter { it.isNotEmpty() }.forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.tertiaryLabel, maxLines = 1)
                                }
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = colors.tertiaryLabel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodePickerSheet(podcastId: UUID, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalTaigaStreamColors.current
    val library = remember { PodcastRepository.get(context) }
    val feeds by library.feeds.collectAsStateWithLifecycle()
    val loading by library.loading.collectAsStateWithLifecycle()
    val failed by library.failed.collectAsStateWithLifecycle()
    library.states.collectAsStateWithLifecycle().value
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val podcast = library.podcast(podcastId)
    val episodes = feeds[podcastId]?.episodes.orEmpty()

    LaunchedEffect(podcastId) {
        if (podcast != null && feeds[podcastId] == null) library.refreshFeed(podcast)
    }

    TaigaSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    podcast?.name?.ifBlank { null } ?: stringResource(R.string.episodes),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionButton(stringResource(R.string.cancel)) { onDismiss() }
            }
            if (podcast == null || episodes.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        podcastId in loading || (feeds[podcastId] == null && podcastId !in failed) ->
                            CircularProgressIndicator()
                        else -> Text(
                            stringResource(
                                if (podcastId in failed) R.string.couldnt_load_episodes else R.string.no_episodes,
                            ),
                            color = colors.tertiaryLabel,
                        )
                    }
                }
            } else {
                val selectedId = library.currentEpisode(podcast)?.id
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = episodes.indexOfFirst { it.id == selectedId }
                        .coerceAtLeast(0).let { (it - 2).coerceAtLeast(0) },
                )
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(episodes, key = { it.id }) { episode ->
                        val isActive = nowPlaying.podcastId == podcastId && nowPlaying.episode?.id == episode.id
                        EpisodeRow(
                            episode = episode,
                            position = if (isActive) nowPlaying.position else library.savedPosition(podcastId, episode),
                            isSelected = episode.id == selectedId,
                            isPlaying = isActive && nowPlaying.isPlaying,
                            onClick = {
                                library.selectEpisode(episode, podcast)
                                if (nowPlaying.podcastId == podcastId) {
                                    PlaybackService.startPlayEpisode(context, podcastId, episode.id)
                                }
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    position: Double,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalTaigaStreamColors.current
    val details = listOfNotNull(
        PodcastFormat.date(episode.publishedAt),
        PodcastFormat.remaining(context, position, episode.duration)
            ?: episode.duration?.let { PodcastFormat.duration(it) },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                isPlaying -> Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = colors.label)
                isSelected -> Icon(Icons.Filled.Check, contentDescription = null, tint = colors.label)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(episode.title.ifEmpty { episode.audioUrl }, color = colors.label, maxLines = 2)
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = colors.tertiaryLabel, maxLines = 1)
            }
            val duration = episode.duration
            if (duration != null && duration > 0 && position > 0) {
                LinearProgressIndicator(
                    progress = { (position / duration).coerceIn(0.0, 1.0).toFloat() },
                    color = colors.tertiaryLabel,
                    trackColor = colors.quaternaryLabel.copy(alpha = 0.4f),
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
