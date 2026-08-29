//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import xyz.andrewmichaelpowell.taigastream.RadioStation
import xyz.andrewmichaelpowell.taigastream.StationRepository
import xyz.andrewmichaelpowell.taigastream.playback.NowPlaying
import xyz.andrewmichaelpowell.taigastream.playback.PlaybackService
import xyz.andrewmichaelpowell.taigastream.search.RadioBrowserClient
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

private sealed interface SheetState {
    data object None : SheetState
    data class Options(val index: Int) : SheetState
    data class Search(val index: Int) : SheetState
    data class ManualEntry(val index: Int, val name: String, val url: String) : SheetState
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { StationRepository.get(context) }
    val stations by repository.stations.collectAsStateWithLifecycle()
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(stations, key = { _, station -> station.id.toString() }) { index, station ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FaviconImage(station.faviconUrl, isConfigured = station.url.isNotEmpty())
                StationRow(
                    station = station,
                    modifier = Modifier.weight(1f),
                    onClick = { sheet = SheetState.Options(index) },
                )
                PlayButton(
                    streamNumber = index + 1,
                    isConfigured = station.url.isNotEmpty(),
                    isActive = nowPlaying.isPlaying && nowPlaying.currentStream == index + 1,
                    onClick = { PlaybackService.startPlaySlot(context, index) },
                )
            }
        }
    }

    when (val current = sheet) {
        is SheetState.Options -> {
            val station = stations.getOrNull(current.index) ?: RadioStation.EMPTY
            StationOptionsSheet(
                index = current.index,
                hasStation = station.url.isNotEmpty(),
                canMoveUp = current.index > 0,
                canMoveDown = current.index < stations.size - 1,
                onDismiss = { sheet = SheetState.None },
                onSearch = { sheet = SheetState.Search(current.index) },
                onEnterUrl = { sheet = SheetState.ManualEntry(current.index, station.name, station.url) },
                onMoveUp = {
                    scope.launch { repository.moveStation(current.index, current.index - 1) }
                    sheet = SheetState.None
                },
                onMoveDown = {
                    scope.launch { repository.moveStation(current.index, current.index + 1) }
                    sheet = SheetState.None
                },
                onClear = {
                    scope.launch { repository.saveStation(RadioStation.EMPTY, current.index) }
                    sheet = SheetState.None
                },
            )
        }

        is SheetState.Search -> {
            RadioBrowserSearchSheet(
                onDismiss = { sheet = SheetState.None },
                onSelect = { result ->
                    scope.launch {
                        repository.saveStation(
                            RadioStation(url = result.url, name = result.name, faviconUrl = result.faviconUrl),
                            current.index,
                        )
                    }
                    RadioBrowserClient.shared.recordClick(result.id)
                    sheet = SheetState.None
                },
            )
        }

        is SheetState.ManualEntry -> {
            ManualUrlSheet(
                initialName = current.name,
                initialUrl = current.url,
                onDismiss = { sheet = SheetState.None },
                onSave = { name, url ->
                    val existingFavicon = stations.getOrNull(current.index)?.faviconUrl ?: ""
                    scope.launch {
                        repository.saveStation(
                            RadioStation(url = url, name = name, faviconUrl = existingFavicon),
                            current.index,
                        )
                    }
                    sheet = SheetState.None
                },
            )
        }

        SheetState.None -> {}
    }
}

@Composable
private fun StationRow(station: RadioStation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalTaigaStreamColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = colors.secondarySystemBackground,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val text = station.name.ifEmpty { station.url }
            if (text.isNotEmpty()) {
                Text(text, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlayButton(
    streamNumber: Int,
    isConfigured: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    val containerColor = if (isActive) colors.mint else colors.secondarySystemBackground

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(50.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isActive) {
                Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White)
            } else {
                Text(
                    "$streamNumber",
                    color = if (isConfigured) colors.label else colors.quaternaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
