//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import xyz.andrewmichaelpowell.taigastream.metadata.FaviconArtwork
import xyz.andrewmichaelpowell.taigastream.ui.theme.LocalTaigaStreamColors

@Composable
fun FaviconImage(
    faviconUrl: String,
    isConfigured: Boolean,
    size: Dp = 36.dp,
    savedStationStyle: Boolean = false,
) {
    val colors = LocalTaigaStreamColors.current
    val context = LocalContext.current
    var failed by remember(faviconUrl) { mutableStateOf(false) }
    var transparent by remember(faviconUrl) { mutableStateOf(false) }
    val hasFavicon = faviconUrl.isNotEmpty() && !failed
    val showsWhiteBackground = savedStationStyle && hasFavicon && transparent
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = Modifier
            .size(size)
            .then(if (savedStationStyle) Modifier.clip(shape) else Modifier)
            .then(if (showsWhiteBackground) Modifier.background(Color.White) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (hasFavicon) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(faviconUrl).allowHardware(false).build(),
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .then(if (showsWhiteBackground) Modifier.scale(FaviconArtwork.TRANSPARENT_ICON_INSET) else Modifier),
                onSuccess = { state ->
                    if (savedStationStyle) {
                        transparent = FaviconArtwork.hasTransparency(state.result.image.toBitmap())
                    }
                },
                onError = { failed = true },
            )
        } else {
            val appIcon = if (savedStationStyle && isConfigured) {
                remember { FaviconArtwork.appIcon(context)?.asImageBitmap() }
            } else {
                null
            }
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(size),
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
}
