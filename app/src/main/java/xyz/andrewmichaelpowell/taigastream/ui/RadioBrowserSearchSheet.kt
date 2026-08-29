//  Taiga Stream
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.andrewmichaelpowell.taigastream.R
import xyz.andrewmichaelpowell.taigastream.search.RadioBrowserClient
import xyz.andrewmichaelpowell.taigastream.search.RadioBrowserStation
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

private const val PAGE_SIZE = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioBrowserSearchSheet(onDismiss: () -> Unit, onSelect: (RadioBrowserStation) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<RadioBrowserStation>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var offset by remember { mutableIntStateOf(0) }

    fun runSearch(reset: Boolean) {
        if (reset) {
            offset = 0
            results = emptyList()
            hasSearched = true
        }
        isLoading = true
        val params = RadioBrowserClient.SearchParams(name = query, limit = PAGE_SIZE, offset = offset)
        RadioBrowserClient.shared.search(params) { stations ->
            scope.launch(Dispatchers.Main) {
                results = if (reset) stations else results + stations
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaigaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotEmpty()) runSearch(true)
                    }),
                )
                OptionButton(
                    enabled = query.isNotEmpty() && !isLoading,
                    onClick = { runSearch(true) },
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    val colors = LocalTaigaStreamColors.current
                    Text(
                        stringResource(R.string.search),
                        fontWeight = FontWeight.Bold,
                        color = if (query.isNotEmpty() && !isLoading) colors.label else colors.tertiaryLabel,
                    )
                }
                OptionButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            }

            if (hasSearched && results.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_results),
                        color = LocalTaigaStreamColors.current.tertiaryLabel,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(results, key = { it.id }) { station ->
                        RadioBrowserResultRow(station, onClick = { onSelect(station) })
                    }
                    val hasFullPage = results.isNotEmpty() && results.size == PAGE_SIZE * (offset / PAGE_SIZE + 1)
                    if (hasFullPage && !isLoading) {
                        item {
                            TextButton(
                                onClick = {
                                    offset += PAGE_SIZE
                                    runSearch(false)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.more_results))
                            }
                        }
                    }
                    if (isLoading && results.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioBrowserResultRow(station: RadioBrowserStation, onClick: () -> Unit) {
    val colors = LocalTaigaStreamColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconImage(station.faviconUrl, isConfigured = true)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            val subtitle = listOfNotNull(
                station.state.takeIf { it.isNotEmpty() },
                station.country.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.tertiaryLabel, maxLines = 1)
            }
            if (station.tags.isNotEmpty()) {
                Text(
                    station.tags.split(",").take(2).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiaryLabel,
                    maxLines = 1,
                )
            }
            if (station.bitrate > 0) {
                Text("${station.bitrate} kbps", style = MaterialTheme.typography.labelSmall, color = colors.tertiaryLabel)
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
