//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyListState
import xyz.andrewmichaelpowell.taigastream.R
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

private const val UI_PREFS = "ui"
private const val SELECTED_TAB_KEY = "selected_tab"

private val ChooserClearance = 88.dp

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableIntStateOf(prefs.getInt(SELECTED_TAB_KEY, 0)) }
    val density = LocalDensity.current
    var panelHeight by remember { mutableStateOf(0.dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = modifier.fillMaxSize()) {
            val listPadding = PaddingValues(top = panelHeight + 8.dp, bottom = ChooserClearance)
            when (selectedTab) {
                0 -> RadioPage(contentPadding = listPadding)
                else -> PodcastsPage(contentPadding = listPadding)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { panelHeight = with(density) { it.height.toDp() } },
                contentAlignment = Alignment.TopCenter,
            ) {
                NowPlayingPanel(modifier = Modifier.padding(top = 8.dp))
            }
            PageChooser(
                selectedIndex = selectedTab,
                items = listOf(
                    Icons.Filled.Radio to stringResource(R.string.radio),
                    Icons.Filled.Mic to stringResource(R.string.podcasts),
                ),
                onSelect = { index ->
                    selectedTab = index
                    prefs.edit { putInt(SELECTED_TAB_KEY, index) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun RadioPage(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember { StationRepository.get(context) }
    val stations by repository.stations.collectAsStateWithLifecycle()
    val nowPlaying by NowPlaying.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<SheetState>(SheetState.None) }

    var rows by remember { mutableStateOf(stations) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LaunchedEffect(stations) {
        if (!reorderState.isAnyItemDragging) rows = stations
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        items(rows, key = { it.id }) { station ->
            val index = rows.indexOf(station)
            ReorderableRow(
                state = reorderState,
                key = station.id,
                onDragStopped = { scope.launch { repository.reorder(rows.map { it.id }) } },
            ) {
                FaviconImage(station.faviconUrl, isConfigured = station.url.isNotEmpty())
                StationRow(
                    station = station,
                    modifier = Modifier.weight(1f),
                    onClick = { sheet = SheetState.Options(index) },
                )
                StationPlayButton(
                    streamNumber = index + 1,
                    isConfigured = station.url.isNotEmpty(),
                    isActive = nowPlaying.isPlaying && !nowPlaying.isPodcastActive &&
                        nowPlaying.currentStream == index + 1,
                    onClick = { PlaybackService.startPlaySlot(context, index) },
                )
            }
        }
        item(key = "add") {
            AddRow(
                label = stringResource(R.string.add_station),
                onClick = { sheet = SheetState.Options(stations.size) },
            )
        }
    }

    when (val current = sheet) {
        is SheetState.Options -> {
            val station = stations.getOrNull(current.index) ?: RadioStation.EMPTY
            StationOptionsSheet(
                index = current.index,
                hasStation = station.url.isNotEmpty(),
                onDismiss = { sheet = SheetState.None },
                onSearch = { sheet = SheetState.Search(current.index) },
                onEnterUrl = { sheet = SheetState.ManualEntry(current.index, station.name, station.url) },
                onClear = {
                    scope.launch { repository.clearStation(current.index) }
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
fun AddRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircleButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = LocalTaigaStreamColors.current.label)
        }
    }
}

@Composable
private fun StationRow(station: RadioStation, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalTaigaStreamColors.current
    Surface(
        onClick = onClick,
        shape = RowShape,
        color = frostedColor(),
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
private fun StationPlayButton(
    streamNumber: Int,
    isConfigured: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalTaigaStreamColors.current
    CircleButton(onClick = onClick, isOn = isActive) {
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
