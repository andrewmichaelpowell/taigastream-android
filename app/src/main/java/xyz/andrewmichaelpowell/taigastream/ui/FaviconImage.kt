//  Taiga Stream
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

/**
 * Ports `FaviconView`/`RadioBrowserResultRow`'s favicon loading (MainView.swift:43-101, 430-509).
 * No background box behind the image — many favicons have transparent backgrounds and are meant
 * to sit directly on the app background, same as iOS. An empty [faviconUrl] or a load failure
 * falls back to an antenna glyph, tinted like the play button's slot number: full label color when
 * [isConfigured] (the station just lacks a favicon), dimmer quaternary color when the slot itself
 * is empty.
 */
@Composable
fun FaviconImage(faviconUrl: String, isConfigured: Boolean, size: Dp = 36.dp) {
    val colors = LocalTaigaStreamColors.current
    var failed by remember(faviconUrl) { mutableStateOf(false) }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (faviconUrl.isNotEmpty() && !failed) {
            AsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier.size(size),
                onError = { failed = true },
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CellTower,
                contentDescription = null,
                tint = if (isConfigured) colors.label else colors.quaternaryLabel,
                modifier = Modifier.size(size),
            )
        }
    }
}
