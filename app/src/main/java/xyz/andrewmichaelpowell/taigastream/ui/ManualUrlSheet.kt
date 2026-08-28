//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.andrewmichaelpowell.taigastream.R

/** Ports `ManualURLSheet` (MainView.swift:654-750). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualUrlSheet(
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.enter_url), style = MaterialTheme.typography.titleMedium)
            TaigaTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Name",
                leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null) },
            )
            TaigaTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = "URL",
                leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            )
            OptionButton(
                text = stringResource(R.string.save),
                enabled = url.isNotBlank(),
                onClick = { onSave(name.trim(), url.trim()) },
            )
            OptionButton(text = stringResource(R.string.cancel), onClick = onDismiss)
        }
    }
}
