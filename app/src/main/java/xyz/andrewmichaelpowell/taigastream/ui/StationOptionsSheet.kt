//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.andrewmichaelpowell.taigastream.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationOptionsSheet(
    index: Int,
    hasStation: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onEnterUrl: () -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    TaigaSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.stream_title, index + 1),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            OptionButton(stringResource(R.string.search)) { onSearch() }
            OptionButton(stringResource(R.string.enter_url)) { onEnterUrl() }
            if (hasStation) {
                OptionButton(stringResource(R.string.clear), destructive = true) { onClear() }
            }
            OptionButton(stringResource(R.string.cancel)) { onDismiss() }
        }
    }
}
